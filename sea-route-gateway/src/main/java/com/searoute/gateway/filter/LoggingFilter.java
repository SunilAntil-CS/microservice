package com.searoute.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs each request after the filter chain completes, with method,
 * path, status, duration, and correlation ID in a structured way.
 * <p>
 * Order is set to run after {@link CorrelationIdFilter} (e.g.
 * {@link Ordered#HIGHEST_PRECEDENCE} + 1) so the correlation ID is already in the
 * exchange attributes. We record the start time before delegating to the chain, then
 * log in a {@code .then()} callback when the chain has finished, so we can read the
 * response status and compute duration. The log output is structured so it can be
 * parsed or enhanced by a JSON encoder (e.g. Logback's logstash-logback-encoder).
 */
@Component
public class LoggingFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    private static final String START_TIME_ATTRIBUTE = "gatewayRequestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTRIBUTE, startTime);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> logRequest(exchange, startTime)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private void logRequest(ServerWebExchange exchange, long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();
        HttpStatus status = exchange.getResponse().getStatusCode();
        String statusCode = status != null ? String.valueOf(status.value()) : "UNKNOWN";
        String correlationId = (String) exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);

        // Structured message for parsing; logstash-logback-encoder can wrap in JSON.
        log.info("gateway_request method={} path={} status={} durationMs={} correlationId={}",
                method, path, statusCode, durationMs, correlationId != null ? correlationId : "-");
    }
}
