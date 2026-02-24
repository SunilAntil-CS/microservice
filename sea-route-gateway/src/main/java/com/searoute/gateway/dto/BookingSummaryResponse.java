package com.searoute.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Combined response for the booking summary (API composition) endpoint.
 * Reduces client round trips by aggregating booking, cargo tracking, and invoice in one call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingSummaryResponse {

    private String bookingId;
    /** Customer identifier or display info from the booking. */
    private String customer;
    /** Cargo/tracking items; may be partial if a backend failed (see warnings). */
    private List<CargoItem> cargo;
    /** Invoice status; may be null if payment service failed (see warnings). */
    private InvoiceSummary invoice;
    /** Warnings when one or more services failed or returned partial data. */
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CargoItem {
        private String trackingId;
        private String status;
        private String location;
        private String lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSummary {
        private String status;
        private String amount;
        private String currency;
    }
}
