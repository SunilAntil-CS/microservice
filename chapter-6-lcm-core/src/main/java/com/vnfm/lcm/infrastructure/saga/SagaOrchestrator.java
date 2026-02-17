package com.vnfm.lcm.infrastructure.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.model.VnfLcmOpOccAggregate;
import com.vnfm.lcm.infrastructure.eventstore.EventStore;
import com.vnfm.lcm.infrastructure.outbox.OutboxMessage;
import com.vnfm.lcm.infrastructure.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_OP_OCC;

/**
 * Orchestrates multi-step (saga) flows for VNF lifecycle. Starts sagas, records state,
 * and runs compensation (e.g. ReleaseResources) on failure. Commands are sent via the outbox.
 */
@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    public static final String SAGA_TYPE_INSTANTIATE = "VNF_INSTANTIATE";
    public static final String SAGA_TYPE_TERMINATE = "VNF_TERMINATE";
    public static final int STEP_RESERVE_RESOURCES = 1;
    public static final int STEP_DEPLOY = 2;
    public static final int STEP_TERMINATE = 1;
    public static final String CMD_RESERVE_RESOURCES = "ReserveResources";
    public static final String CMD_RELEASE_RESOURCES = "ReleaseResources";
    public static final String CMD_TERMINATE_VNF = "TerminateVnf";
    public static final String DESTINATION_VIM = "vim.manager";

    private final SagaInstanceRepository sagaRepository;
    private final SagaTimeoutRepository sagaTimeoutRepository;
    private final OutboxRepository outboxRepository;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    @Value("${lcm.saga.step-timeout-seconds:120}")
    private int stepTimeoutSeconds;

    public SagaOrchestrator(SagaInstanceRepository sagaRepository,
                            SagaTimeoutRepository sagaTimeoutRepository,
                            OutboxRepository outboxRepository,
                            EventStore eventStore,
                            ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.sagaTimeoutRepository = sagaTimeoutRepository;
        this.outboxRepository = outboxRepository;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Start a new instantiation saga: persist the instance and send the first command (ReserveResources) via outbox.
     * operationId links to the LCM operation occurrence for completion/failure updates.
     */
    @Transactional
    public UUID startInstantiateSaga(String vnfId, Map<String, Object> resources) {
        return startInstantiateSaga(vnfId, null, resources);
    }

    /**
     * Start instantiation saga with operation occurrence ID (ETSI flow).
     */
    @Transactional
    public UUID startInstantiateSaga(String vnfId, UUID operationId, Map<String, Object> resources) {
        UUID sagaId = UUID.randomUUID();
        String sagaStateJson = "{\"completedSteps\":[],\"stepResults\":{}}";

        SagaInstance saga = new SagaInstance(sagaId, vnfId, operationId, SAGA_TYPE_INSTANTIATE, STEP_RESERVE_RESOURCES, sagaStateJson);
        sagaRepository.save(saga);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sagaId", sagaId.toString());
        payload.put("vnfId", vnfId);
        if (operationId != null) {
            payload.put("operationId", operationId.toString());
        }
        payload.put("resources", resources != null ? resources : Map.of());

        OutboxMessage outbox = new OutboxMessage(
                UUID.randomUUID().toString(),
                DESTINATION_VIM,
                CMD_RESERVE_RESOURCES,
                writeJson(payload)
        );
        outboxRepository.save(outbox);

        Instant executeAt = Instant.now().plusSeconds(stepTimeoutSeconds);
        SagaTimeout timeout = new SagaTimeout(sagaId.toString(), STEP_RESERVE_RESOURCES, executeAt);
        sagaTimeoutRepository.save(timeout);

        log.info("Started instantiate saga sagaId={} vnfId={}", sagaId, vnfId);
        return sagaId;
    }

    /**
     * Start a terminate saga: persist the instance and send TerminateVnf command via outbox.
     */
    @Transactional
    public UUID startTerminateSaga(String vnfId) {
        UUID sagaId = UUID.randomUUID();
        String sagaStateJson = "{\"completedSteps\":[],\"stepResults\":{}}";

        SagaInstance saga = new SagaInstance(sagaId, vnfId, SAGA_TYPE_TERMINATE, STEP_TERMINATE, sagaStateJson);
        sagaRepository.save(saga);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sagaId", sagaId.toString());
        payload.put("vnfId", vnfId);

        OutboxMessage outbox = new OutboxMessage(
                UUID.randomUUID().toString(),
                DESTINATION_VIM,
                CMD_TERMINATE_VNF,
                writeJson(payload)
        );
        outboxRepository.save(outbox);

        Instant executeAt = Instant.now().plusSeconds(stepTimeoutSeconds);
        SagaTimeout timeout = new SagaTimeout(sagaId.toString(), STEP_TERMINATE, executeAt);
        sagaTimeoutRepository.save(timeout);

        log.info("Started terminate saga sagaId={} vnfId={}", sagaId, vnfId);
        return sagaId;
    }

    /**
     * Process a reply for a given saga step. On success, advance to next step (or complete);
     * on failure, start compensation (e.g. send ReleaseResources if step 1 had succeeded).
     */
    @Transactional
    public void handleReply(UUID sagaId, int step, boolean success, Map<String, Object> result) {
        SagaInstance saga = sagaRepository.findBySagaId(sagaId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));

        if (saga.getStatus() != SagaStatus.RUNNING && saga.getStatus() != SagaStatus.COMPENSATING) {
            log.warn("Saga {} not in RUNNING state, ignoring reply for step {}", sagaId, step);
            return;
        }

        markTimeoutProcessed(sagaId.toString(), step);

        Map<String, Object> state = parseSagaState(saga.getSagaState());
        @SuppressWarnings("unchecked")
        List<Object> completedSteps = (List<Object>) state.getOrDefault("completedSteps", new ArrayList<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> stepResults = (Map<String, Object>) state.getOrDefault("stepResults", new HashMap<String, Object>());

        stepResults.put(String.valueOf(step), Map.of("success", success, "result", result != null ? result : Map.of()));

        if (success) {
            if (!completedSteps.stream().anyMatch(s -> Objects.equals(s, step) || (s instanceof Number && ((Number) s).intValue() == step))) {
                completedSteps.add(step);
            }
            state.put("completedSteps", completedSteps);
            state.put("stepResults", stepResults);
            saga.setSagaState(writeJson(state));
            saga.setUpdatedAt(java.time.Instant.now());

            if (step == STEP_RESERVE_RESOURCES) {
                saga.setCurrentStep(STEP_DEPLOY);
                sagaRepository.save(saga);
                log.info("Saga {} step 1 (ReserveResources) succeeded, advanced to step 2", sagaId);
            } else if (step == STEP_DEPLOY) {
                saga.setStatus(SagaStatus.COMPLETED);
                sagaRepository.save(saga);
                completeOperationOccurrence(saga.getOperationId(), true, null);
                log.info("Saga {} completed successfully", sagaId);
            } else if (SAGA_TYPE_TERMINATE.equals(saga.getSagaType()) && step == STEP_TERMINATE) {
                saga.setStatus(SagaStatus.COMPLETED);
                sagaRepository.save(saga);
                log.info("Saga {} (terminate) completed successfully", sagaId);
            } else {
                sagaRepository.save(saga);
            }
        } else {
            saga.setStatus(SagaStatus.COMPENSATING);
            state.put("stepResults", stepResults);
            saga.setSagaState(writeJson(state));
            saga.setUpdatedAt(java.time.Instant.now());

            boolean step1Completed = completedSteps.stream().anyMatch(s -> Objects.equals(s, STEP_RESERVE_RESOURCES) || (s instanceof Number && ((Number) s).intValue() == STEP_RESERVE_RESOURCES));
            if (step1Completed && SAGA_TYPE_INSTANTIATE.equals(saga.getSagaType())) {
                Map<String, Object> compPayload = new HashMap<>();
                compPayload.put("sagaId", sagaId.toString());
                compPayload.put("vnfId", saga.getVnfId());
                compPayload.put("reason", "step " + step + " failed");
                OutboxMessage releaseCmd = new OutboxMessage(
                        UUID.randomUUID().toString(),
                        DESTINATION_VIM,
                        CMD_RELEASE_RESOURCES,
                        writeJson(compPayload)
                );
                outboxRepository.save(releaseCmd);
                log.info("Saga {} step {} failed; sent ReleaseResources (step 1 had succeeded)", sagaId, step);
            } else {
                log.info("Saga {} step {} failed; no compensation (step 1 not completed)", sagaId, step);
            }

            saga.setStatus(SagaStatus.FAILED);
            sagaRepository.save(saga);
            String reason = (result != null && result.get("reason") != null) ? result.get("reason").toString() : "step " + step + " failed";
            completeOperationOccurrence(saga.getOperationId(), false, reason);
        }
    }

    /** Update operation occurrence aggregate on saga completion or failure (ETSI). */
    private void completeOperationOccurrence(String operationId, boolean success, String errorMessage) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        try {
            UUID opId = UUID.fromString(operationId);
            List<DomainEvent> events = eventStore.loadEvents(opId, AGGREGATE_TYPE_OP_OCC);
            if (events.isEmpty()) {
                return;
            }
            VnfLcmOpOccAggregate agg = VnfLcmOpOccAggregate.from(events);
            List<DomainEvent> newEvents = success
                    ? agg.processComplete()
                    : agg.processFail(errorMessage);
            eventStore.saveEvents(opId, AGGREGATE_TYPE_OP_OCC, newEvents, agg.getVersion());
        } catch (Exception e) {
            log.warn("Could not update operation occurrence {}: {}", operationId, e.getMessage());
        }
    }

    private Map<String, Object> parseSagaState(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not parse saga state: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize saga payload", e);
        }
    }

    /**
     * Mark any pending timeout for this saga+step as processed (reply arrived before timeout).
     * Called from handleReply and from TimeoutScheduler after handling a timeout.
     */
    @Transactional
    public void markTimeoutProcessed(String sagaId, int step) {
        List<SagaTimeout> timeouts = sagaTimeoutRepository.findBySagaIdAndStepAndProcessedFalse(sagaId, step);
        for (SagaTimeout t : timeouts) {
            t.setProcessed(true);
            sagaTimeoutRepository.save(t);
        }
    }
}
