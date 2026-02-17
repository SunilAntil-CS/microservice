package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when an LCM operation occurrence is created (e.g. instantiate started).
 */
public class OpOccCreated extends AbstractDomainEvent {

    private final String vnfId;
    private final String operationType;  // INSTANTIATE, TERMINATE, etc.

    public OpOccCreated(String aggregateId, String vnfId, String operationType, int version, Instant timestamp) {
        super(aggregateId, version, timestamp);
        this.vnfId = vnfId;
        this.operationType = operationType;
    }

    public OpOccCreated(String eventId, String aggregateId, String vnfId, String operationType,
                        int version, Instant timestamp) {
        super(eventId, aggregateId, version, timestamp);
        this.vnfId = vnfId;
        this.operationType = operationType;
    }

    public String getVnfId() {
        return vnfId;
    }

    public String getOperationType() {
        return operationType;
    }
}
