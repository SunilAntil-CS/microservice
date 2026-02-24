package com.searoute.gateway.proxy;

import com.searoute.gateway.dto.Invoice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive proxy for the payment/invoice backend service.
 * The proxy layer isolates gateway logic from backend communication; the circuit breaker
 * prevents cascading failures when the backend is slow or down. Fallback: {@link Invoice#empty()}.
 */
@Component
public class PaymentServiceProxy {

    private static final String CIRCUIT_BREAKER_NAME = "paymentService";

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public PaymentServiceProxy(@Qualifier("paymentServiceWebClient") WebClient webClient,
                               ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = webClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Fetches invoice status for a booking. Uses the circuit breaker with fallback to
     * {@link Invoice#empty()} on failure.
     */
    public Mono<Invoice> getInvoiceByBookingId(String bookingId) {
        Mono<Invoice> call = webClient
                .get()
                .uri("/api/v1/invoices/booking/{bookingId}", bookingId)
                .retrieve()
                .bodyToMono(Invoice.class);

        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(call, t -> Mono.just(Invoice.empty()));
    }
}
