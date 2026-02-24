package com.searoute.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a booking returned by the booking service.
 * Uses {@link JsonIgnoreProperties}(ignoreUnknown = true) so that extra fields
 * from the backend do not cause deserialisation failures.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Booking {

    private String id;
    private String reference;
    private String status;
    private String customerId;
    private String vesselId;
    private String origin;
    private String destination;
    /** Container or tracking IDs for cargo; used by API composition to fetch tracking. */
    private java.util.List<String> containerIds;

    /**
     * Returns an empty booking instance, e.g. for circuit breaker fallback when
     * the backend is unavailable.
     */
    public static Booking empty() {
        return Booking.builder()
                .id(null)
                .reference(null)
                .status("UNAVAILABLE")
                .customerId(null)
                .vesselId(null)
                .origin(null)
                .destination(null)
                .containerIds(null)
                .build();
    }
}
