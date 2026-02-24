package com.searoute.gateway.config;

import com.searoute.gateway.filter.BookingSummaryGatewayFilter;
import com.searoute.gateway.handler.BookingSummaryHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines gateway routes using a programmatic {@link RouteLocator}.
 * <p>
 * A {@link RouteLocator} builds routes from <em>predicates</em> (e.g. path matching) and
 * <em>filters</em> (e.g. adding headers, rate limiting, rewriting paths). Each route maps
 * a request pattern to a backend URI.
 * <p>
 * When Eureka is enabled, the {@code lb://SERVICE-ID} prefix enables client-side load
 * balancing via service discovery: the gateway resolves the service name to instances
 * and forwards requests to them. Without discovery, we use fixed URIs (e.g.
 * {@code http://localhost:8081}); these will be replaced with {@code lb://} when
 * Eureka is configured.
 * <p>
 * All {@code /api/v1/**} routes use the RequestRateLimiter gateway filter with a
 * Redis-backed token bucket (see {@link RateLimiterConfig}); rate limit headers are
 * included in responses when {@code spring.cloud.gateway.filter.request-rate-limiter.include-headers}
 * is true.
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
    public GatewayFilter bookingSummaryFilter(BookingSummaryHandler bookingSummaryHandler) {
        return new BookingSummaryGatewayFilter(bookingSummaryHandler);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder,
                                          KeyResolver userKeyResolver,
                                          RedisRateLimiter redisRateLimiter,
                                          GatewayFilter bookingSummaryFilter) {
        return builder.routes()
                // API composition: only when client calls .../summary do we aggregate booking + cargo + invoice.
                // This route must be before /api/v1/bookings/** so it matches first; otherwise individual
                // APIs (e.g. GET /api/v1/bookings/123) are forwarded to the booking backend.
                .route("booking-summary", r -> r
                        .path("/api/v1/bookings/{id}/summary")
                        .filters(f -> f.filter(bookingSummaryFilter))
                        .uri("http://localhost:0"))  // not used; filter handles response
                .route("bookings", r -> r.path("/api/v1/bookings/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "sea-route")
                                .requestRateLimiter(c -> c
                                        .setKeyResolver(userKeyResolver)
                                       .setRateLimiter(redisRateLimiter)))
                        .uri(BOOKING_SERVICE_URI))  // or lb://BOOKING-SERVICE with Eureka
                .route("tracking", r -> r.path("/api/v1/tracking/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "sea-route")
                                .requestRateLimiter(c -> c
                                        .setKeyResolver(userKeyResolver)
                                        .setRateLimiter(redisRateLimiter)))
                        .uri(CARGO_TRACKING_SERVICE_URI))  // or lb://CARGO-TRACKING-SERVICE
                .route("schedules", r -> r.path("/api/v1/schedules/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Source", "sea-route")
                                .requestRateLimiter(c -> c
                                        .setKeyResolver(userKeyResolver)
                                        .setRateLimiter(redisRateLimiter)))
                        .uri(VESSEL_SCHEDULE_SERVICE_URI))  // or lb://VESSEL-SCHEDULE-SERVICE
                .build();
    }
}
