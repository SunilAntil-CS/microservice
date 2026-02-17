package com.telecom.mediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MODULE 3: Mediation Service — Transactional Outbox Pattern.
 *
 * CONCEPT: Parses raw CDRs, saves to DB (audit), and "sends" an event to Billing.
 * We never write to Kafka inside the same transaction as the DB; we write to an
 * outbox table in the same DB transaction. A relay job then reads the outbox and
 * publishes to Kafka. This avoids the Dual Write Problem and guarantees atomicity.
 *
 * @EnableScheduling: Enables @Scheduled methods (our OutboxRelay runs every 500ms
 * to poll the message table and publish to Kafka). Required for the relay; without
 * it, @Scheduled would do nothing.
 */
@SpringBootApplication
@EnableScheduling
public class MediationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediationApplication.class, args);
    }
}
