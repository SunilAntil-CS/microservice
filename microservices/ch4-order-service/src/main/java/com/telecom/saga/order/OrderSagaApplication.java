package com.telecom.saga.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ORDER SERVICE - Saga Orchestrator (Chapter 4 Module 1).
 * ---------------------------------------------------------------------------
 * CONCEPT: In a microservices saga, this service is the "orchestrator." It owns
 * the Order aggregate and coordinates multi-step workflows that span the
 * Kitchen Service (and optionally Accounting). It does NOT use a single
 * @Transactional across services—instead it runs a sequence of local
 * transactions and compensations (undo) when a step fails.
 * ---------------------------------------------------------------------------
 * @SpringBootApplication: Enables component scan, auto-configuration, and
 * the embedded web server so we can expose REST and call Kitchen via WebClient.
 */
@SpringBootApplication
public class OrderSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderSagaApplication.class, args);
    }
}
