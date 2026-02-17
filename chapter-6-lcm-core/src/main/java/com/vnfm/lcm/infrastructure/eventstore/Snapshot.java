package com.vnfm.lcm.infrastructure.eventstore;

import java.util.UUID;

/**
 * Value type for a stored snapshot: aggregate state at a given version.
 */
public record Snapshot(
        UUID aggregateId,
        int version,
        String payload
) {
}
