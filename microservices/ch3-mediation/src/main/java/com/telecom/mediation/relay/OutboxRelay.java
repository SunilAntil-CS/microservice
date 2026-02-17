package com.telecom.mediation.relay;

import com.telecom.mediation.model.OutboxMessage;
import com.telecom.mediation.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MODULE 3: The "Relay" — moves messages from the outbox table to Kafka.
 *
 * CONCEPT: This is NOT part of the business transaction. It runs as a background
 * job. It SELECTs rows WHERE published = 0, sends the payload to Kafka, then marks
 * published = 1 (or deletes). If Kafka is down, we don't lose the message — it stays
 * in the table and we retry next poll. At-least-once delivery: we might send the
 * same message twice if we crash after Kafka send but before marking published, so
 * consumers must be idempotent (Module 4).
 *
 * @Scheduled(fixedDelayString = "500"): Runs every 500ms after the previous run
 * completes. fixedDelayString reads from config (e.g. mediation.relay.interval-ms).
 * Alternative: fixedRate = 500 runs every 500ms from start regardless of duration.
 * We use fixedDelay so we don't pile up runs if Kafka is slow.
 *
 * KafkaTemplate: Spring Kafka's API to send messages. send(topic, payload) is
 * async by default; we could use get() to block and handle failure (e.g. don't
 * mark published on exception). For simplicity we mark published after send; in
 * production you might only mark after Kafka acknowledges.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxMessageRepository outboxMessageRepository,
                      KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${mediation.relay.interval-ms:500}")
    public void publishUnpublished() {
        List<OutboxMessage> unpublished = outboxMessageRepository.findByPublishedOrderByCreationTimeAsc(0);
        for (OutboxMessage msg : unpublished) {
            try {
                kafkaTemplate.send(msg.getDestination(), msg.getPayload());
                markPublished(msg.getId());
            } catch (Exception e) {
                log.warn("Failed to publish outbox message id={}, will retry: {}", msg.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void markPublished(String id) {
        outboxMessageRepository.findById(id).ifPresent(m -> {
            m.setPublished(1);
            outboxMessageRepository.save(m);
        });
    }
}
