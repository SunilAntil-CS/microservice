package com.searoute.gateway.filter;

import com.searoute.gateway.handler.BookingSummaryHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that delegates to {@link BookingSummaryHandler} for the booking summary
 * (API composition) endpoint. Builds a {@link ServerRequest} from the exchange, calls
 * the handler, and writes the handler's {@link ServerResponse} to the exchange. Does not
 * call {@link GatewayFilterChain#filter(ServerWebExchange)} so the request is not
 * forwarded to a backend.
 */
public class BookingSummaryGatewayFilter implements GatewayFilter {

    private static final HandlerStrategies HANDLER_STRATEGIES = HandlerStrategies.withDefaults();
    private static final ServerResponse.Context RESPONSE_CONTEXT = new ServerResponse.Context() {
        @Override
        public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
            return HANDLER_STRATEGIES.messageWriters();
        }
        @Override
        public java.util.List<org.springframework.web.reactive.result.view.ViewResolver> viewResolvers() {
            return HANDLER_STRATEGIES.viewResolvers();
        }
    };

    private final BookingSummaryHandler handler;

    public BookingSummaryGatewayFilter(BookingSummaryHandler handler) {
        this.handler = handler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerRequest request = ServerRequest.create(exchange, HANDLER_STRATEGIES.messageReaders());
        return handler.getSummary(request)
                .flatMap(response -> response.writeTo(exchange, RESPONSE_CONTEXT))
                .then();
    }
}
