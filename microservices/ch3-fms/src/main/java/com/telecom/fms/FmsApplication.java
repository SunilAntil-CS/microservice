package com.telecom.fms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MODULE 4: Fault Management System (FMS) — Idempotent Consumer.
 *
 * CONCEPT: Consumes "Link Down" (and similar) events from Kafka. The broker and
 * network can deliver the same message more than once (at-least-once). We must
 * create ONE trouble ticket per logical event, not one per delivery. The
 * deduplication guard: insert (consumer_id, message_id) into received_messages
 * in the SAME transaction as creating the ticket. Duplicate key → skip processing.
 */
@SpringBootApplication
public class FmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FmsApplication.class, args);
    }
}
