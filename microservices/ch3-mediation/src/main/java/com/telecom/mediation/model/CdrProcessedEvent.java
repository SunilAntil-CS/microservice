package com.telecom.mediation.model;

import java.math.BigDecimal;

/**
 * Domain event: "CDR was processed; billing can charge this amount."
 *
 * CONCEPT: This is the payload we put in the outbox (and later in Kafka). The
 * Billing service consumes it and charges the subscriber. We use a POJO (plain
 * Java object) with a stable shape; Jackson serialises it to JSON for the outbox
 * payload and for Kafka.
 *
 * No JPA annotations — this is not an entity, just a DTO for the event payload.
 * Keep fields serialisation-friendly (no complex types) so JSON round-trip is clean.
 */
public record CdrProcessedEvent(
        String cdrId,
        String subscriberId,
        BigDecimal cost
) {}
