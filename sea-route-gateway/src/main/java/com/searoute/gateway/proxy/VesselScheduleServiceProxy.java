package com.searoute.gateway.proxy;

import com.searoute.gateway.dto.VesselSchedule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive proxy for the vessel schedule backend service.
 * <p>
 * The proxy layer isolates gateway logic from backend communication; the circuit breaker
 * prevents cascading failures when the backend is slow or down. Timeouts on the WebClient
 * instance ensure the gateway does not hang indefinitely. Fallback: {@link VesselSchedule#empty()}.
 */
@Component
public class VesselScheduleServiceProxy {

    private static final String CIRCUIT_BREAKER_NAME = "scheduleService";

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public VesselScheduleServiceProxy(@Qualifier("scheduleServiceWebClient") WebClient webClient,
                                     ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = webClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Fetches a vessel schedule by schedule ID. Uses the circuit breaker with
     * fallback to an empty schedule instance on failure.
     */
    public Mono<VesselSchedule> getSchedule(String scheduleId) {
        Mono<VesselSchedule> call = webClient
                .get()
                .uri("/api/v1/schedules/{id}", scheduleId)
                .retrieve()
                .bodyToMono(VesselSchedule.class);

        return circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME)
                .run(call, t -> Mono.just(VesselSchedule.empty()));
    }
}
