package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when an LCM operation fails.
 */
public class OpOccFailed extends AbstractDomainEvent {

    private final String errorMessage;

    public OpOccFailed(String aggregateId, String errorMessage, int version, Instant timestamp) {
        super(aggregateId, version, timestamp);
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public OpOccFailed(String eventId, String aggregateId, String errorMessage, int version, Instant timestamp) {
        super(eventId, aggregateId, version, timestamp);
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
