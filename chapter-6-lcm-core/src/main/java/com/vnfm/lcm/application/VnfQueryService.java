package com.vnfm.lcm.application;

import com.vnfm.lcm.api.dto.VnfInstance;
import com.vnfm.lcm.api.dto.VnfLcmOpOcc;
import com.vnfm.lcm.api.dto.VnfStateResponse;
import com.vnfm.lcm.api.dto.VnfSummary;
import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.event.VnfInstanceCreated;
import com.vnfm.lcm.domain.model.VnfAggregate;
import com.vnfm.lcm.domain.model.VnfLcmOpOccAggregate;
import com.vnfm.lcm.domain.model.VnfState;
import com.vnfm.lcm.infrastructure.eventstore.EventStore;
import static com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_VNF;
import com.vnfm.lcm.infrastructure.readside.VnfIndex;
import com.vnfm.lcm.infrastructure.readside.VnfIndexRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Projects VNF state from the event store and read-side index for REST API.
 */
@Service
public class VnfQueryService {

    private final EventStore eventStore;
    private final VnfIndexRepository vnfIndexRepository;

    public VnfQueryService(EventStore eventStore, VnfIndexRepository vnfIndexRepository) {
        this.eventStore = eventStore;
        this.vnfIndexRepository = vnfIndexRepository;
    }

    public boolean exists(String vnfId) {
        return vnfIndexRepository.existsById(vnfId);
    }

    public VnfStateResponse getVnfState(String vnfId) {
        UUID id = UUID.fromString(vnfId);
        List<DomainEvent> events = eventStore.loadEvents(id, AGGREGATE_TYPE_VNF);
        if (events.isEmpty()) {
            return new VnfStateResponse(vnfId, VnfState.INITIAL.name(), 0, null, null);
        }
        VnfAggregate aggregate = VnfAggregate.from(events);
        return new VnfStateResponse(
                aggregate.getVnfId() != null ? aggregate.getVnfId().toString() : vnfId,
                aggregate.getState().name(),
                aggregate.getVersion(),
                aggregate.getVimResourceId(),
                aggregate.getIpAddress()
        );
    }

    public List<VnfSummary> listVnfs() {
        List<VnfIndex> index = vnfIndexRepository.findAllByOrderByCreatedAtDesc();
        return index.stream()
                .map(v -> {
                    VnfStateResponse state = getVnfState(v.getVnfId());
                    return new VnfSummary(v.getVnfId(), state.getState());
                })
                .collect(Collectors.toList());
    }

    /**
     * Get ETSI VnfInstance DTO for GET /vnflcm/v1/vnf_instances/{vnfId}.
     */
    public VnfInstance getVnfInstance(String vnfId) {
        UUID id = UUID.fromString(vnfId);
        List<DomainEvent> events = eventStore.loadEvents(id, AGGREGATE_TYPE_VNF);
        return VnfLcmApplicationService.buildVnfInstanceFromEvents(vnfId, events);
    }

    /**
     * List all VNF instances for GET /vnflcm/v1/vnf_instances.
     */
    public List<VnfInstance> listVnfInstances() {
        List<VnfIndex> index = vnfIndexRepository.findAllByOrderByCreatedAtDesc();
        return index.stream()
                .map(v -> getVnfInstance(v.getVnfId()))
                .collect(Collectors.toList());
    }

    /**
     * Get operation occurrence for GET /vnflcm/v1/vnf_lcm_op_occs/{opId}.
     */
    public VnfLcmOpOcc getOperationOccurrence(String opId) {
        UUID id = UUID.fromString(opId);
        List<DomainEvent> events = eventStore.loadEvents(id, com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_OP_OCC);
        if (events.isEmpty()) {
            return null;
        }
        VnfLcmOpOccAggregate agg = VnfLcmOpOccAggregate.from(events);
        VnfLcmOpOcc dto = new VnfLcmOpOcc();
        dto.setId(agg.getOpId().toString());
        dto.setOperation(agg.getOperationType().name());
        dto.setState(agg.getState().name());
        dto.setVnfInstanceId(agg.getVnfId());
        dto.setStartTime(agg.getStartTime());
        dto.setEndTime(agg.getEndTime());
        if (agg.getErrorMessage() != null && !agg.getErrorMessage().isEmpty()) {
            dto.setError(new VnfLcmOpOcc.ProblemDetails(agg.getErrorMessage(), "Operation failed", 500));
        }
        return dto;
    }
}
