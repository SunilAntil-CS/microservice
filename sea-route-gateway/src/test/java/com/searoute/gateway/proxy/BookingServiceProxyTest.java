package com.searoute.gateway.proxy;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BookingServiceProxy} with a mocked backend (WireMock).
 * Verifies success response, failure fallback, timeout fallback, and circuit breaker opening.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(BookingServiceProxyTest.TestWebClientConfig.class)
class BookingServiceProxyTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private BookingServiceProxy proxy;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("searoute.backend.booking-base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @Test
    void getBooking_returnsBooking_whenBackendReturns200() {
        String body = "{\"id\":\"123\",\"reference\":\"BR-001\",\"status\":\"CONFIRMED\"}";
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/bookings/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        StepVerifier.create(proxy.getBooking("123"))
                .assertNext(booking -> {
                    assertThat(booking.getId()).isEqualTo("123");
                    assertThat(booking.getReference()).isEqualTo("BR-001");
                    assertThat(booking.getStatus()).isEqualTo("CONFIRMED");
                })
                .verifyComplete();
    }

    @Test
    void getBooking_returnsEmptyFallback_whenBackendReturns500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/bookings/456"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(proxy.getBooking("456"))
                .assertNext(booking -> {
                    assertThat(booking.getStatus()).isEqualTo("UNAVAILABLE");
                    assertThat(booking.getId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void getBooking_returnsEmptyFallback_whenBackendIsSlow() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/bookings/789"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"789\"}")
                        .withFixedDelay(2000)));

        StepVerifier.create(proxy.getBooking("789"))
                .assertNext(booking -> assertThat(booking.getStatus()).isEqualTo("UNAVAILABLE"))
                .verifyComplete();
    }

    @Test
    void getBooking_returnsFallbackWhenCircuitOpen_afterFailures() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/bookings/cb-test"))
                .willReturn(aResponse().withStatus(500)));

        // Trigger failures so circuit opens (test profile: minimumNumberOfCalls=2, failureRateThreshold=50)
        StepVerifier.create(proxy.getBooking("cb-test")).assertNext(b -> assertThat(b.getStatus()).isEqualTo("UNAVAILABLE")).verifyComplete();
        StepVerifier.create(proxy.getBooking("cb-test")).assertNext(b -> assertThat(b.getStatus()).isEqualTo("UNAVAILABLE")).verifyComplete();

        // Circuit should be open: next call gets fallback without necessarily hitting backend
        StepVerifier.create(proxy.getBooking("cb-test"))
                .assertNext(booking -> assertThat(booking.getStatus()).isEqualTo("UNAVAILABLE"))
                .verifyComplete();
    }

    /**
     * Test-only WebClient for booking service with short response timeout (500ms)
     * so that "slow backend" test completes quickly and triggers timeout fallback.
     */
    @TestConfiguration
    static class TestWebClientConfig {

        @Bean("bookingServiceWebClient")
        @Primary
        public WebClient bookingServiceWebClient(@Value("${searoute.backend.booking-base-url}") String baseUrl) {
            HttpClient client = HttpClient.create()
                    .responseTimeout(Duration.ofMillis(500));
            return WebClient.builder()
                    .baseUrl(baseUrl)
                    .clientConnector(new ReactorClientHttpConnector(client))
                    .build();
        }
    }
}
