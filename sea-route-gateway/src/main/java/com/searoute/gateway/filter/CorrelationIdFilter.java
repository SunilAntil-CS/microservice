package com.searoute.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Global filter that ensures every request has a correlation ID for distributed tracing.
 * <p>
 * A correlation ID ties together logs and spans across the gateway and downstream services,
 * making it possible to trace a single request end-to-end. If the client sends
 * {@code X-Correlation-ID}, we reuse it; otherwise we generate a new UUID.
 * <p>
 * We store the ID in the exchange's attributes so other filters (e.g. {@link LoggingFilter})
 * can use it. In a traditional servlet stack, MDC (Mapped Diagnostic Context) would allow
 * all subsequent logs on the same thread to include the correlation ID automatically. In
 * reactive WebFlux, the thread can change as the pipeline executes, so we use exchange
 * attributes here; full reactive MDC would require propagating the ID via Reactor's
 * {@link reactor.util.context.Context} (e.g. with a custom context writer and
 * logback-reactor or similar). This filter keeps the simple approach: attributes + header.
 * <p>
 * {@link Ordered#getOrder()} is set to {@link Ordered#HIGHEST_PRECEDENCE} so this filter
 * runs before other global filters (e.g. logging), ensuring the correlation ID is available
 * for all downstream processing.
 */
@Component
public class CorrelationIdFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    /** Header name for correlation ID (incoming and forwarded to backends). */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** Exchange attribute key so other filters can read the correlation ID. */
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String correlationId = getOrCreateCorrelationId(exchange.getRequest());
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(CORRELATION_ID_HEADER, correlationId))
                .build();

        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String getOrCreateCorrelationId(ServerHttpRequest request) {
        List<String> values = request.getHeaders().get(CORRELATION_ID_HEADER);
        if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
            return values.get(0).trim();
        }
        return UUID.randomUUID().toString();
    }
}
