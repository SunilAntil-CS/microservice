package com.searoute.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for cargo tracking data returned by the tracking service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tracking {

    private String id;
    private String bookingId;
    private String status;
    private String location;
    private String lastUpdated;

    /**
     * Returns an empty tracking instance for circuit breaker fallback.
     */
    public static Tracking empty() {
        return Tracking.builder()
                .id(null)
                .bookingId(null)
                .status("UNAVAILABLE")
                .location(null)
                .lastUpdated(null)
                .build();
    }
}
