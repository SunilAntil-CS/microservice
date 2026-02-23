package com.searoute.gateway.proxy;

import com.searoute.gateway.dto.Tracking;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive proxy for the cargo tracking backend service.
 * <p>
 * The proxy layer isolates gateway logic from backend communication; the circuit breaker
 * prevents cascading failures when the backend is slow or down. Timeouts on the WebClient
 * instance ensure the gateway does not hang indefinitely. Fallback: {@link Tracking#empty()}.
 */
@Component
public class CargoTrackingServiceProxy {

    private static final String CIRCUIT_BREAKER_NAME = "trackingService";

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public CargoTrackingServiceProxy(@Qualifier("trackingServiceWebClient") WebClient webClient,
                                    ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = webClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Fetches tracking information by tracking ID. Uses the circuit breaker with
     * fallback to an empty tracking instance on failure.
     */
    public Mono<Tracking> getTracking(String trackingId) {
        Mono<Tracking> call = webClient
                .get()
                .uri("/api/v1/tracking/{id}", trackingId)
                .retrieve()
                .bodyToMono(Tracking.class);

        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(call, t -> Mono.just(Tracking.empty()));
    }
}
