package com.telecom.ordergrpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MODULE 5: gRPC Order Service — High-Performance Inter-Process Communication.
 *
 * CONCEPT: Contract-first. The .proto file defines the service and messages; Maven
 * generates Java stubs; we implement the server. Binary protocol (Protocol Buffers)
 * over HTTP/2: lower latency and bandwidth than REST/JSON for internal service-to-service
 * calls (e.g. telecom AMF–SMF at 10k req/s). REST remains for external/public APIs.
 */
@SpringBootApplication
public class OrderGrpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderGrpcApplication.class, args);
    }
}
