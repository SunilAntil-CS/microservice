package com.searoute.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configures {@link WebClient} beans for backend service calls.
 * <p>
 * We use two layers so the gateway does not get stuck: (1) <strong>HTTP-level timeouts</strong>
 * here in WebClientConfig, and (2) the <strong>circuit breaker</strong> in the proxy. They do
 * different jobs. The circuit breaker does <em>not</em> stop a single request from hanging: it
 * only reacts to <em>failures</em> (errors, timeouts) and after a threshold opens to fail fast.
 * Without HTTP timeouts, a slow backend would never produce a failure—the request would just
 * hang—so the circuit breaker would never see a timeout and could not release resources or open.
 * So we set connect/response/read/write timeouts on the WebClient: they abort the request and
 * release the connection after a limit, produce a failure the circuit breaker can count, and
 * trigger the fallback. The circuit breaker then protects the gateway from cascading calls once
 * many such failures have occurred.
 */
@Configuration
public class WebClientConfig {

    /** Connect timeout: fail fast if the backend does not accept the connection. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Response/read/write timeout: fail and trigger fallback if the backend does not respond in time. */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CONNECTIONS = 100;
    private static final int PENDING_ACQUIRE_MAX_COUNT = 500;

    @Value("${searoute.backend.booking-base-url}")
    private String bookingBaseUrl;

    @Value("${searoute.backend.tracking-base-url}")
    private String trackingBaseUrl;

    @Value("${searoute.backend.schedule-base-url}")
    private String scheduleBaseUrl;

    /**
     * Shared HTTP client: connect timeout, response (read) timeout, and a fixed
     * connection pool. Reused by all service WebClients. These timeouts ensure
     * the gateway does not block indefinitely when a backend is slow or down.
     */
    @Bean
    public HttpClient gatewayHttpClient() {
        ConnectionProvider provider = ConnectionProvider.builder("gateway-pool")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireMaxCount(PENDING_ACQUIRE_MAX_COUNT)
                .maxIdleTime(Duration.ofSeconds(20))
                .build();
        return HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(RESPONSE_TIMEOUT)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler((int) RESPONSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler((int) RESPONSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)));
    }

    @Bean
    public WebClient bookingServiceWebClient(HttpClient gatewayHttpClient) {
        return WebClient.builder()
                .baseUrl(bookingBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(gatewayHttpClient))
                .build();
    }

    @Bean
    public WebClient trackingServiceWebClient(HttpClient gatewayHttpClient) {
        return WebClient.builder()
                .baseUrl(trackingBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(gatewayHttpClient))
                .build();
    }

    @Bean
    public WebClient scheduleServiceWebClient(HttpClient gatewayHttpClient) {
        return WebClient.builder()
                .baseUrl(scheduleBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(gatewayHttpClient))
                .build();
    }
}
