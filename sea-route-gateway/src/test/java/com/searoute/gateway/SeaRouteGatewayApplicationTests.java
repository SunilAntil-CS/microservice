package com.searoute.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the Spring application context loads successfully.
 * Uses {@code src/test/resources/application.yml} to disable Eureka client so the test runs without a discovery server.
 */
@SpringBootTest
class SeaRouteGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
