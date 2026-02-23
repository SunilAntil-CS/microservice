package com.searoute.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway tests using {@link WebTestClient} to hit the gateway HTTP layer.
 * Mocks are not used here; this verifies that the gateway starts and responds
 * (e.g. actuator). For proxy behaviour with mocked backend responses, see
 * {@link com.searoute.gateway.proxy.BookingServiceProxyTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayWebTestClientTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealth_returns200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
