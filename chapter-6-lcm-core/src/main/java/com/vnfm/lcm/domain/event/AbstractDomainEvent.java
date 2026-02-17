package com.vnfm.lcm.domain.event;

import com.vnfm.lcm.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Base implementation for domain events with common fields set at construction.
 */
public abstract class AbstractDomainEvent implements DomainEvent {

    private final String eventId;
    private final String aggregateId;
    private final int version;
    private final Instant timestamp;

    protected AbstractDomainEvent(String aggregateId, int version, Instant timestamp) {
        this(UUID.randomUUID().toString(), aggregateId, version, timestamp);
    }

    /** Constructor for rebuilding from store (preserves eventId). */
    protected AbstractDomainEvent(String eventId, String aggregateId, int version, Instant timestamp) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.version = version;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
