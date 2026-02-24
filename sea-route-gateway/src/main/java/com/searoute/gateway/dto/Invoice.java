package com.searoute.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for invoice/payment status returned by the payment service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Invoice {

    private String id;
    private String bookingId;
    private String status;
    private String amount;
    private String currency;

    /**
     * Returns an empty invoice for circuit breaker fallback.
     */
    public static Invoice empty() {
        return Invoice.builder()
                .id(null)
                .bookingId(null)
                .status("UNAVAILABLE")
                .amount(null)
                .currency(null)
                .build();
    }
}
