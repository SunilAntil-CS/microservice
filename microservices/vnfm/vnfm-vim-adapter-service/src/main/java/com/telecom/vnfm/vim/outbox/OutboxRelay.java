package com.telecom.vnfm.vim.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Phase 4: Outbox Relay — reads VIM outbox and publishes reply events to Kafka.
 * ---------------------------------------------------------------------------
 * Polls unpublished rows on a schedule, sends payload to the reply topic
 * (destination), sets "message_id" header for LCM's idempotent consumer (Phase 5).
 *
 * SYNC SEND & DELETE-AFTER-PUBLISH:
 * - We use kafkaTemplate.send(record).get(timeout, unit) so the relay thread blocks
 *   until Kafka confirms the write. No callbacks: if send fails, .get() throws and
 *   we do not delete the row, so the next run will retry.
 * - After successful send we call OutboxMessageRepository.deleteMessageAfterPublish(id).
 *   That is a separate, external repository call (no self-invocation), so Spring's
 *   transaction proxy applies correctly. The delete runs in REQUIRES_NEW inside the
 *   repository layer, committing the removal of the row.
 *
 * WHY DELETE INSTEAD OF "MARK PUBLISHED" (published=1)?
 * At high TPS, updating rows to published=1 would leave millions of rows in the
 * outbox table. Table and index size grow unbounded; queries slow down; retention
 * and archival become operational headaches. Deleting rows after successful publish
 * keeps the table small—only unpublished (in-flight) messages remain. This is
 * standard practice for transactional outbox in high-scale telecom/production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${vnfm.vim.outbox.relay.enabled:true}")
    private boolean relayEnabled;

    @Value("${vnfm.vim.outbox.relay.batch-size:50}")
    private int batchSize;

    @Value("${vnfm.vim.outbox.relay.send-timeout-seconds:10}")
    private int sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${vnfm.vim.outbox.relay.interval-ms:2000}")
    public void publishUnpublished() {
        if (!relayEnabled) return;

        List<OutboxMessageEntity> batch = outboxMessageRepository
                .findByPublishedOrderByIdAsc(0, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;

        for (OutboxMessageEntity message : batch) {
            try {
                sendToKafka(message);
                // Separate repository call (not self-invocation): delete runs in its own
                // transaction via deleteMessageAfterPublish's REQUIRES_NEW in the repository.
                outboxMessageRepository.deleteMessageAfterPublish(message.getId());
            } catch (Exception e) {
                log.warn("Outbox relay failed for messageId={}, will retry next run: {}", message.getId(), e.getMessage());
            }
        }
    }

    /**
     * Synchronous send: block until Kafka acks or timeout. No callback—.get() throws
     * on failure so the caller can skip delete and retry on next run.
     */
    private void sendToKafka(OutboxMessageEntity message) throws Exception {
        String topic = message.getDestination();
        String key = message.getId();
        String value = message.getPayload();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value);
        record.headers().add(new RecordHeader("message_id", message.getId().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        log.debug("Outbox published reply: messageId={}, topic={}", key, topic);
    }
}
