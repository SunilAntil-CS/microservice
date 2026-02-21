package com.searoute.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Sea Route API Gateway.
 *
 * {@code @SpringBootApplication} enables Spring Boot auto-configuration and component scanning
 * for this package and all sub-packages (config, filter, handler, proxy, dto).
 */
@SpringBootApplication
public class SeaRouteGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeaRouteGatewayApplication.class, args);
    }
}
