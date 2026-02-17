package com.telecom.vnfm.lcm.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RELAY: Reads outbox table and publishes to Kafka (production pattern).
 * ---------------------------------------------------------------------------
 * WHY CHOREOGRAPHY OVER ORCHESTRATION? For a strictly 2-actor system (LCM and
 * VIM Adapter), a central orchestrator (Temporal, Camunda) would be overkill.
 * Choreography gives perfect decoupling: each service reacts to events and
 * emits new events. The Outbox Relay is the bridge from our DB to Kafka—we
 * never call Kafka inside the business transaction, so we avoid the dual-write
 * problem. The Idempotent Consumer on the other side (using processed_messages)
 * ensures exactly-once processing despite at-least-once delivery.
 *
 * HARD-DELETE STRATEGY:
 * 1. Poll unpublished rows (batched).
 * 2. Send each to Kafka with kafkaTemplate.send(record).get(timeout) for
 *    synchronous broker ACK—no fire-and-forget.
 * 3. On success: DELETE the row (not update published=1) to prevent table bloat.
 * 4. On send failure: do not delete; next run retries (at-least-once).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${vnfm.lcm.outbox.relay.enabled:true}")
    private boolean relayEnabled;

    @Value("${vnfm.lcm.outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${vnfm.lcm.outbox.relay.send-timeout-seconds:10}")
    private int sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${vnfm.lcm.outbox.relay.interval-ms:2000}")
    public void publishUnpublished() {
        if (!relayEnabled) {
            return;
        }

        List<OutboxMessageEntity> batch = outboxMessageRepository
                .findByPublishedOrderByIdAsc(0, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxMessageEntity message : batch) {
            try {
                sendToKafka(message);
                outboxMessageRepository.deleteMessageAfterPublish(message.getId());
            } catch (Exception e) {
                log.warn("Outbox relay failed for messageId={}, will retry next run: {}", message.getId(), e.getMessage());
            }
        }
    }

    /**
     * Synchronous send: block until Kafka acks or timeout. Ensures we only
     * delete the outbox row after the broker has accepted the message.
     */
    private void sendToKafka(OutboxMessageEntity message) throws ExecutionException, InterruptedException, TimeoutException {
        String topic = message.getDestination();
        String key = message.getId();
        String value = message.getPayload();

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value);
        record.headers().add(new RecordHeader("message_id", message.getId().getBytes(StandardCharsets.UTF_8)));

        SendResult<String, String> result = kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        if (result == null) {
            throw new IllegalStateException("Send result was null for messageId=" + key);
        }
        log.debug("Outbox published: messageId={}, topic={}, partition={}", key, topic, result.getRecordMetadata().partition());
    }
}
