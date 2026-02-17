package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when an LCM operation completes successfully.
 */
public class OpOccCompleted extends AbstractDomainEvent {

    public OpOccCompleted(String aggregateId, int version, Instant timestamp) {
        super(aggregateId, version, timestamp);
    }

    public OpOccCompleted(String eventId, String aggregateId, int version, Instant timestamp) {
        super(eventId, aggregateId, version, timestamp);
    }
}
