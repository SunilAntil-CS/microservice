package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when an LCM operation occurrence state is updated (e.g. STARTING -> PROCESSING).
 */
public class OpOccUpdated extends AbstractDomainEvent {

    private final String state;

    public OpOccUpdated(String aggregateId, String state, int version, Instant timestamp) {
        super(aggregateId, version, timestamp);
        this.state = state;
    }

    public OpOccUpdated(String eventId, String aggregateId, String state, int version, Instant timestamp) {
        super(eventId, aggregateId, version, timestamp);
        this.state = state;
    }

    public String getState() {
        return state;
    }
}
