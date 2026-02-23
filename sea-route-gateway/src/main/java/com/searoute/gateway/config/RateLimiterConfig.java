package com.searoute.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Configuration for the gateway's Redis-based request rate limiter.
 * <p>
 * Rate limiting uses the <strong>token bucket</strong> algorithm: each client (identified by
 * the key from {@link #userKeyResolver()}) has a bucket that holds up to {@code burstCapacity}
 * tokens. Each request consumes {@code requestedTokens} (default 1). Tokens are replenished at
 * {@code replenishRate} per second. If a request arrives and the bucket has insufficient tokens,
 * the gateway returns 429 Too Many Requests. Using Redis as the backend provides
 * <strong>distributed rate limiting</strong> across multiple gateway instances, so limits are
 * enforced consistently even when load is spread over several nodes.
 */
@Configuration
public class RateLimiterConfig {

    /** Tokens added to the bucket per second (sustained request rate). */
    private static final int REPLENISH_RATE = 10;
    /** Maximum tokens in the bucket (allows bursts above the sustained rate). */
    private static final int BURST_CAPACITY = 20;

    /**
     * Resolves the rate-limit key per request so that each client is limited independently.
     * <p>
     * If the request carries {@code X-User-ID} (set by {@link com.searoute.gateway.filter.JwtAuthenticationFilter}
     * after JWT validation), that value is used as the key so limits are per authenticated user.
     * Otherwise the client's IP address is used, so unauthenticated or legacy clients are
     * limited per IP. This choice ensures fair usage per user when possible, and per host when
     * user context is not available.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            String host = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(host);
        };
    }

    /**
     * Redis-backed rate limiter used by the RequestRateLimiter gateway filter.
     * Uses token bucket with the configured replenish rate and burst capacity;
     * each request consumes one token by default.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(REPLENISH_RATE, BURST_CAPACITY);
    }
}
