package com.cqrs.policyquery.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized policy event (from Kafka payload). Matches command-side PolicyEvent.
 */
public record PolicyEventPayload(
        UUID eventId,
        String subscriberId,
        Instant timestamp,
        String policyName,
        boolean decision,
        long quotaUsed
) {
    @JsonCreator
    public PolicyEventPayload(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("subscriberId") String subscriberId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("policyName") String policyName,
            @JsonProperty("decision") boolean decision,
            @JsonProperty("quotaUsed") long quotaUsed) {
        this.eventId = eventId;
        this.subscriberId = subscriberId;
        this.timestamp = timestamp;
        this.policyName = policyName;
        this.decision = decision;
        this.quotaUsed = quotaUsed;
    }
}
