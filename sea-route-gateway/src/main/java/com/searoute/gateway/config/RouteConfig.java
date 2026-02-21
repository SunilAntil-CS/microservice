package com.searoute.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines gateway routes using a programmatic {@link RouteLocator}.
 * <p>
 * A {@link RouteLocator} builds routes from <em>predicates</em> (e.g. path matching) and
 * <em>filters</em> (e.g. adding headers, rewriting paths). Each route maps a request
 * pattern to a backend URI.
 * <p>
 * When Eureka is enabled, the {@code lb://SERVICE-ID} prefix enables client-side load
 * balancing via service discovery: the gateway resolves the service name to instances
 * and forwards requests to them. Without discovery, we use fixed URIs (e.g.
 * {@code http://localhost:8081}); these will be replaced with {@code lb://} when
 * Eureka is configured.
 * 
 *
 */
@Configuration
public class RouteConfig {

    /**
     * Fixed base URIs when service discovery is not available. Replace with
     * {@code lb://BOOKING-SERVICE}, etc., once Eureka is running.
     */
    private static final String BOOKING_SERVICE_URI = "http://localhost:8081";
    private static final String CARGO_TRACKING_SERVICE_URI = "http://localhost:8082";
    private static final String VESSEL_SCHEDULE_SERVICE_URI = "http://localhost:8083";

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("bookings", r -> r.path("/api/v1/bookings/**")
                        .filters(f -> f.addRequestHeader("X-Gateway-Source", "sea-route"))
                        .uri(BOOKING_SERVICE_URI))  // or lb://BOOKING-SERVICE with Eureka
                .route("tracking", r -> r.path("/api/v1/tracking/**")
                        .filters(f -> f.addRequestHeader("X-Gateway-Source", "sea-route"))
                        .uri(CARGO_TRACKING_SERVICE_URI))  // or lb://CARGO-TRACKING-SERVICE
                .route("schedules", r -> r.path("/api/v1/schedules/**")
                        .filters(f -> f.addRequestHeader("X-Gateway-Source", "sea-route"))
                        .uri(VESSEL_SCHEDULE_SERVICE_URI))  // or lb://VESSEL-SCHEDULE-SERVICE
                .build();
    }
}
