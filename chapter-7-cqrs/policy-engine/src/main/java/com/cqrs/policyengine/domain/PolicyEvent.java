package com.cqrs.policyengine.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event for a policy decision (command side).
 * Written to outbox and streamed to Kafka via Debezium.
 */
public record PolicyEvent(
        UUID eventId,
        String subscriberId,
        Instant timestamp,
        String policyName,
        boolean decision,
        long quotaUsed
) {}
