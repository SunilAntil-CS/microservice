package com.telecom.saga.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KITCHEN SERVICE - Saga Participant (Chapter 4 Module 1).
 * ---------------------------------------------------------------------------
 * CONCEPT: This service is a "participant" in the Create Order Saga. It does
 * not orchestrate; it only executes commands from the Order Service:
 *   - Create Ticket (forward step)
 *   - Cancel Ticket (compensating step)
 * Each command is a local transaction (one DB commit). The orchestrator
 * decides when to call the compensating command if a later saga step fails.
 */
@SpringBootApplication
public class KitchenSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenSagaApplication.class, args);
    }
}
