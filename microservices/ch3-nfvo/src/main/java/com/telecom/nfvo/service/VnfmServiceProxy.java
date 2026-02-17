package com.telecom.nfvo.service;

import com.telecom.nfvo.configuration.VnfmServiceDestinations;
import com.telecom.nfvo.model.VnfHealthStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * MODULE 1 + MODULE 2: Resilient HTTP client for VNFM using Service Discovery.
 *
 * Responsibilities: build URL from config, non-blocking HTTP call, timeout, circuit breaker, fallback.
 * ------------------------------------------------------------------------------------
 * SPRING ANNOTATION:
 * ------------------------------------------------------------------------------------
 * @Service
 *   - Specialisation of @Component. Tells Spring to create a bean and inject it where
 *     needed (e.g. into VnfHealthController). Semantically "this is a service-layer bean."
 *   - In other frameworks: Quarkus @ApplicationScoped; Micronaut @Singleton; in NestJS
 *     @Injectable() for a provider.
 * ------------------------------------------------------------------------------------
 * RESILIENCE4J ANNOTATION (resilience4j-spring-boot3):
 * ------------------------------------------------------------------------------------
 * @CircuitBreaker(name = "vnfm-circuit", fallbackMethod = "fallbackHealth")
 *   - Wraps the method in a circuit breaker. "vnfm-circuit" is the instance name; config
 *     is in application.properties under resilience4j.circuitbreaker.instances.vnfm-circuit.
 *   - On exception (timeout, 5xx, connection error), the framework does NOT rethrow—it
 *     calls fallbackHealth(vnfId, throwable) and returns that result. Fallback method must
 *     have same return type and same parameters as original method, plus one extra parameter
 *     Throwable (or optional same params only in some setups).
 *   - When circuit is OPEN (too many failures), the real method is not called at all;
 *     fallback is invoked immediately. This avoids hammering a failing service.
 *   - In other frameworks: Quarkus has SmallRye Fault Tolerance (@CircuitBreaker, @Fallback);
 *     in Node you might use opossum or a custom wrapper; in Go, manual state or a library.
 * ------------------------------------------------------------------------------------
 * TYPES AND CLASSES:
 * ------------------------------------------------------------------------------------
 * org.slf4j.Logger / LoggerFactory
 *   - SLF4J = Simple Logging Facade for Java. Logger is the interface; LoggerFactory.getLogger(...)
 *     returns an implementation (e.g. Logback, which Spring Boot uses by default). We use log.warn(...)
 *     so operations can see why a call failed. Not a Spring class—standard logging facade.
 *
 * WebClient (org.springframework.web.reactive.function.client.WebClient)
 *   - Spring's non-blocking HTTP client (reactive). Replaces RestTemplate for async usage.
 *   - .get().uri(...).retrieve().bodyToMono(...) builds a GET request, sends it, and converts
 *     the response body to a Mono<T>. No thread is blocked waiting for the network.
 *   - WebClient.Builder is auto-configured by Spring Boot when spring-boot-starter-webflux is
 *     on classpath; we inject the builder so Spring can add metrics/tracing if present.
 *   - In other frameworks: In Node, fetch or axios; in Go, net/http or resty; Quarkus has
 *     RestClient (reactive) or RestClientBuilder.
 *
 * reactor.core.publisher.Mono<T>
 *   - From Project Reactor (reactive-streams implementation). Mono = "at most one value" (or
 *     empty, or error), delivered asynchronously. Like a Future/CompletableFuture but with
 *     a richer API (operators, backpressure). bodyToMono(VnfHealthStatus.class) returns
 *     Mono<VnfHealthStatus>—when the HTTP response arrives, the value is emitted.
 *   - .timeout(Duration) cancels the subscription if no value is emitted in that time—critical
 *     so a slow VNFM doesn't hold a thread or block the caller indefinitely.
 *   - Mono.just(x) creates a Mono that immediately emits x (used in fallback).
 *   - In other frameworks: RxJava has Single; Kotlin has Flow; in Node, Promise is the equivalent.
 *
 * java.time.Duration (JDK 8+)
 *   - Represents a time amount (e.g. 2 seconds). Duration.ofSeconds(2) used for timeout.
 *   - Standard library; no Spring dependency.
 * ------------------------------------------------------------------------------------
 * Constructor injection: Spring provides WebClient.Builder (auto-configured) and
 * VnfmServiceDestinations (our @ConfigurationProperties bean). Preferred over field
 * injection for testability and required dependencies.
 * ------------------------------------------------------------------------------------
 * Fallback method signature: Must return same type (Mono<VnfHealthStatus>), same first
 * parameters (vnfId), and one extra parameter Throwable. Resilience4j invokes it when
 * the circuit opens or when the decorated method throws.
 */
@Service
public class VnfmServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(VnfmServiceProxy.class);

    private final WebClient client;
    private final VnfmServiceDestinations destinations;

    public VnfmServiceProxy(WebClient.Builder builder, VnfmServiceDestinations destinations) {
        this.client = builder.build();
        this.destinations = destinations;
    }

    @CircuitBreaker(name = "vnfm-circuit", fallbackMethod = "fallbackHealth")
    public Mono<VnfHealthStatus> getVnfHealth(String vnfId) {
        String url = destinations.getUrl() + "/vnf_instances/{id}/health";
        return client.get()
                .uri(url, vnfId)   // Replaces {id} with vnfId (URI template; safe from injection)
                .retrieve()        // Send request; throws WebClientResponseException on 4xx/5xx
                .bodyToMono(VnfHealthStatus.class)  // Deserialise JSON body to VnfHealthStatus
                .timeout(Duration.ofSeconds(2));   // Cancel if no response in 2s
    }

    public Mono<VnfHealthStatus> fallbackHealth(String vnfId, Throwable t) {
        log.warn("VNFM call failed for vnfId={}: {}", vnfId, t.getMessage());
        return Mono.just(new VnfHealthStatus(vnfId, "UNKNOWN", "VNFM Unreachable"));
    }
}
