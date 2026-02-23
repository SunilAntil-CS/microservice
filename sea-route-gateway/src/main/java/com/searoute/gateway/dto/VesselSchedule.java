package com.searoute.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a vessel schedule returned by the schedule service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VesselSchedule {

    private String id;
    private String vesselId;
    private String voyageNumber;
    private String eta;
    private String etd;
    private String port;

    /**
     * Returns an empty schedule instance for circuit breaker fallback.
     */
    public static VesselSchedule empty() {
        return VesselSchedule.builder()
                .id(null)
                .vesselId(null)
                .voyageNumber(null)
                .eta(null)
                .etd(null)
                .port(null)
                .build();
    }
}
