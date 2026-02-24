package com.searoute.gateway.config;

import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.context.annotation.Configuration;

/**
 * Placeholder for optional tracing customisation.
 * <p>
 * <strong>Distributed tracing</strong> tracks a request across the gateway and downstream
 * services (booking, tracking, payment, etc.), which is crucial for debugging latency and
 * understanding call flows. With {@code micrometer-tracing-bridge-brave} and
 * {@code zipkin-reporter-brave}, Spring Boot auto-configuration provides a {@link io.micrometer.tracing.Tracer}
 * bean and integrates with Spring Cloud Gateway so that <strong>X-B3-* headers</strong>
 * (trace ID, span ID, sampling) are propagated to downstream services. No custom bean is
 * required unless you need to override defaults; this class documents that behaviour.
 */
@Configuration
@ConditionalOnEnabledTracing
public class TracingConfig {
}
