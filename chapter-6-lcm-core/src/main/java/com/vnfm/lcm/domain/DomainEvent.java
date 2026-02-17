package com.vnfm.lcm.domain;

import java.time.Instant;

/**
 * Represents a domain event in the VNF lifecycle.
 * All events carry an identity, aggregate reference, version, and timestamp.
 */
public interface DomainEvent {

    String getEventId();

    String getAggregateId();

    int getVersion();

    Instant getTimestamp();
}
