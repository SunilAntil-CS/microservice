package com.searoute.gateway.proxy;

import com.searoute.gateway.dto.Booking;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive proxy for the booking backend service.
 * <p>
 * A <strong>separate proxy layer</strong> isolates gateway logic from backend communication
 * details: URLs, timeouts, retries, and error handling are confined here so that route
 * and filter logic stays simple. The <strong>circuit breaker</strong> prevents
 * cascading failures when a backend is slow or down: after a threshold of failures it
 * opens and returns the fallback immediately instead of calling the backend, so the
 * gateway remains responsive. Timeouts (configured on the WebClient instance) ensure
 * the gateway does not hang indefinitely on slow backends.
 * <p>
 * On failure, timeout, or open circuit, the fallback (e.g. {@link Booking#empty()}) is
 * returned so callers get a consistent response shape instead of an error.
 */
@Component
public class BookingServiceProxy {

    private static final String CIRCUIT_BREAKER_NAME = "bookingService";

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public BookingServiceProxy(@Qualifier("bookingServiceWebClient") WebClient bookingServiceWebClient,
                               ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = bookingServiceWebClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Fetches a booking by ID. Uses the circuit breaker; on timeout, backend error, or
     * open circuit, returns an empty booking via the fallback.
     */
    public Mono<Booking> getBooking(String bookingId) {
        Mono<Booking> call = webClient
                .get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .retrieve()
                .bodyToMono(Booking.class);

        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(call, t -> Mono.just(Booking.empty()));
    }
}
