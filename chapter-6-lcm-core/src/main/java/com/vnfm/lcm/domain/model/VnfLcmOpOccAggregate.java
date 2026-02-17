package com.vnfm.lcm.domain.model;

import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.event.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event-sourced aggregate for a single LCM operation occurrence.
 * Tracks state: STARTING -> PROCESSING -> COMPLETED/FAILED.
 */
public class VnfLcmOpOccAggregate {

    private UUID opId;
    private String vnfId;
    private LcmOperationType operationType;
    private OpOccState state;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private int version;

    private VnfLcmOpOccAggregate() {
        this.version = 0;
    }

    public static VnfLcmOpOccAggregate from(List<DomainEvent> events) {
        VnfLcmOpOccAggregate a = new VnfLcmOpOccAggregate();
        for (DomainEvent e : events) {
            a.applyEvent(e);
        }
        return a;
    }

    private void applyEvent(DomainEvent e) {
        if (e instanceof OpOccCreated evt) {
            apply(evt);
        } else if (e instanceof OpOccUpdated evt) {
            apply(evt);
        } else if (e instanceof OpOccCompleted evt) {
            apply(evt);
        } else if (e instanceof OpOccFailed evt) {
            apply(evt);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + e.getClass().getName());
        }
    }

    /**
     * Create a new operation occurrence: returns a single OpOccCreated event.
     */
    public static List<DomainEvent> processCreate(UUID opId, String vnfId, LcmOperationType operationType) {
        OpOccCreated event = new OpOccCreated(
                opId.toString(),
                vnfId,
                operationType.name(),
                1,
                Instant.now()
        );
        return List.of(event);
    }

    public List<DomainEvent> processUpdateState(OpOccState newState) {
        if (state == OpOccState.COMPLETED || state == OpOccState.FAILED) {
            throw new IllegalStateException("Cannot update state when already " + state);
        }
        OpOccUpdated event = new OpOccUpdated(opId.toString(), newState.name(), version + 1, Instant.now());
        return List.of(event);
    }

    public List<DomainEvent> processComplete() {
        if (state == OpOccState.COMPLETED || state == OpOccState.FAILED) {
            throw new IllegalStateException("Operation already finished: " + state);
        }
        OpOccCompleted event = new OpOccCompleted(opId.toString(), version + 1, Instant.now());
        return List.of(event);
    }

    public List<DomainEvent> processFail(String errorMessage) {
        if (state == OpOccState.COMPLETED || state == OpOccState.FAILED) {
            throw new IllegalStateException("Operation already finished: " + state);
        }
        OpOccFailed event = new OpOccFailed(opId.toString(), errorMessage, version + 1, Instant.now());
        return List.of(event);
    }

    public void apply(OpOccCreated event) {
        ensureVersion(event.getVersion());
        this.opId = UUID.fromString(event.getAggregateId());
        this.vnfId = event.getVnfId();
        this.operationType = LcmOperationType.valueOf(event.getOperationType());
        this.state = OpOccState.STARTING;
        this.startTime = event.getTimestamp();
        this.version = event.getVersion();
    }

    public void apply(OpOccUpdated event) {
        ensureVersion(event.getVersion());
        this.state = OpOccState.valueOf(event.getState());
        this.version = event.getVersion();
    }

    public void apply(OpOccCompleted event) {
        ensureVersion(event.getVersion());
        this.state = OpOccState.COMPLETED;
        this.endTime = event.getTimestamp();
        this.version = event.getVersion();
    }

    public void apply(OpOccFailed event) {
        ensureVersion(event.getVersion());
        this.state = OpOccState.FAILED;
        this.errorMessage = event.getErrorMessage();
        this.endTime = event.getTimestamp();
        this.version = event.getVersion();
    }

    private void ensureVersion(int eventVersion) {
        if (eventVersion != version + 1) {
            throw new IllegalStateException(
                    "Event version " + eventVersion + " does not follow current version " + version);
        }
    }

    public UUID getOpId() {
        return opId;
    }

    public String getVnfId() {
        return vnfId;
    }

    public LcmOperationType getOperationType() {
        return operationType;
    }

    public OpOccState getState() {
        return state;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getVersion() {
        return version;
    }
}
