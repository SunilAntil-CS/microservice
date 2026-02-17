package com.telecom.mediation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

/**
 * Inbound DTO: raw CDR as received from the Switch/Tower (e.g. via REST).
 *
 * CONCEPT: The mediation service receives this and (1) parses/stores CdrEntity,
 * (2) writes an outbox message for the Billing event. We keep the input shape
 * simple for the example.
 *
 * @NotBlank / @NotNull / @Positive: Jakarta Bean Validation. When the controller
 * uses @Valid on the method parameter, Spring validates before the method runs.
 * Invalid payload → 400 Bad Request. Same as in Module 2 (VnfmServiceDestinations).
 */
public record RawCdr(
        @NotBlank(message = "callId is required") String callId,
        @NotBlank(message = "subscriberId is required") String subscriberId,
        @NotNull @Positive Long durationSeconds,
        String cellId
) {
    public RawCdr {
        Objects.requireNonNull(callId);
        Objects.requireNonNull(subscriberId);
        Objects.requireNonNull(durationSeconds);
    }
}
