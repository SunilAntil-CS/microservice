package com.vnfm.lcm.application;

import com.vnfm.lcm.api.dto.CreateVnfInstanceRequest;
import com.vnfm.lcm.api.dto.InstantiateVnfRequestLcm;
import com.vnfm.lcm.api.dto.VnfInstance;
import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.command.InstantiateVnfCommand;
import com.vnfm.lcm.domain.event.VnfInstanceCreated;
import com.vnfm.lcm.domain.event.VnfInstantiationStarted;
import com.vnfm.lcm.domain.model.LcmOperationType;
import com.vnfm.lcm.domain.model.VnfAggregate;
import com.vnfm.lcm.domain.model.VnfLcmOpOccAggregate;
import com.vnfm.lcm.domain.model.VnfState;
import com.vnfm.lcm.infrastructure.eventstore.EventStore;
import com.vnfm.lcm.infrastructure.readside.VnfIndex;
import com.vnfm.lcm.infrastructure.readside.VnfIndexRepository;
import com.vnfm.lcm.infrastructure.saga.SagaOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_OP_OCC;
import static com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_VNF;

/**
 * Application service for ETSI-compliant VNF LCM operations:
 * create VNF instance, start instantiation (creates operation occurrence and starts saga).
 */
@Service
public class VnfLcmApplicationService {

    private final EventStore eventStore;
    private final VnfIndexRepository vnfIndexRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public VnfLcmApplicationService(EventStore eventStore,
                                    VnfIndexRepository vnfIndexRepository,
                                    SagaOrchestrator sagaOrchestrator) {
        this.eventStore = eventStore;
        this.vnfIndexRepository = vnfIndexRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    /**
     * Create a new VNF instance (POST /vnf_instances). Emits VnfInstanceCreated and persists.
     */
    @Transactional
    public VnfInstance createVnfInstance(CreateVnfInstanceRequest request) {
        UUID vnfId = UUID.randomUUID();
        String name = request != null ? request.getVnfInstanceName() : null;
        String description = request != null ? request.getVnfInstanceDescription() : null;

        VnfInstanceCreated event = new VnfInstanceCreated(
                vnfId.toString(), name, description, 1, Instant.now());
        eventStore.saveEvents(vnfId, AGGREGATE_TYPE_VNF, List.of(event), 0);

        vnfIndexRepository.save(new VnfIndex(vnfId.toString()));

        return buildVnfInstanceFromEvents(vnfId.toString(), List.of(event));
    }

    /**
     * Start instantiation: create OpOcc aggregate (STARTING), emit VnfInstantiationStarted, start saga, return operation ID.
     * Caller must have verified VNF is in NOT_INSTANTIATED state.
     */
    @Transactional
    public UUID startInstantiation(String vnfId, InstantiateVnfRequestLcm request) {
        UUID vnfUuid = UUID.fromString(vnfId);
        List<DomainEvent> vnfEvents = eventStore.loadEvents(vnfUuid, AGGREGATE_TYPE_VNF);
        if (vnfEvents.isEmpty()) {
            throw new IllegalArgumentException("VNF instance not found: " + vnfId);
        }
        VnfAggregate vnfAggregate = VnfAggregate.from(vnfEvents);
        if (vnfAggregate.getState() != VnfState.INITIAL) {
            throw new IllegalStateException("VNF instance must be in NOT_INSTANTIATED state to instantiate; current: " + vnfAggregate.getState());
        }

        InstantiateVnfCommand command = new InstantiateVnfCommand(
                vnfId,
                request != null && request.getFlavourId() != null ? request.getFlavourId() : "default",
                2,
                4,
                request != null ? request.getRequestId() : null);
        List<DomainEvent> startedEvents = vnfAggregate.process(command);
        eventStore.saveEvents(vnfUuid, AGGREGATE_TYPE_VNF, startedEvents, vnfAggregate.getVersion());

        UUID opId = UUID.randomUUID();
        List<DomainEvent> opOccEvents = VnfLcmOpOccAggregate.processCreate(
                opId, vnfId, LcmOperationType.INSTANTIATE);
        eventStore.saveEvents(opId, AGGREGATE_TYPE_OP_OCC, opOccEvents, 0);

        Map<String, Object> resources = buildResourcesFromRequest(request);
        sagaOrchestrator.startInstantiateSaga(vnfId, opId, resources);

        return opId;
    }

    private Map<String, Object> buildResourcesFromRequest(InstantiateVnfRequestLcm request) {
        if (request == null) {
            return Map.of();
        }
        return new java.util.HashMap<>(Map.of(
                "flavourId", nullToEmpty(request.getFlavourId()),
                "instantiationLevelId", nullToEmpty(request.getInstantiationLevelId()),
                "extVirtualLinks", request.getExtVirtualLinks() != null ? request.getExtVirtualLinks() : List.of(),
                "additionalParams", request.getAdditionalParams() != null ? request.getAdditionalParams() : Map.of()
        ));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /** Build VnfInstance DTO from event list (first event must be VnfInstanceCreated for name/desc). */
    public static VnfInstance buildVnfInstanceFromEvents(String vnfId, List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return new VnfInstance(vnfId, "NOT_INSTANTIATED", null, null, null, null, null);
        }
        VnfAggregate aggregate = VnfAggregate.from(events);
        String name = null;
        String description = null;
        Instant createdAt = null;
        if (events.get(0) instanceof VnfInstanceCreated first) {
            name = first.getVnfInstanceName();
            description = first.getVnfInstanceDescription();
            createdAt = first.getTimestamp();
        }
        String instantiationState = mapToInstantiationState(aggregate.getState());
        return new VnfInstance(
                vnfId,
                instantiationState,
                name,
                description,
                aggregate.getVimResourceId(),
                aggregate.getIpAddress(),
                createdAt
        );
    }

    private static String mapToInstantiationState(VnfState state) {
        return state == VnfState.ACTIVE ? "INSTANTIATED" : "NOT_INSTANTIATED";
    }
}
