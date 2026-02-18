package com.vnfm.vimmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.vimmanager.domain.ProcessedCommandRepository;
import com.vnfm.vimmanager.outbox.OutboxRepository;
import com.vnfm.vimmanager.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test with embedded Kafka: produce to vim.commands, assert idempotency (processed_commands)
 * and reply written to outbox, then forwarded to vim.replies.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "vim.commands", "vim.replies" }, bootstrapServersProperty = "spring.embedded.kafka.brokers")
@DirtiesContext
class VimManagerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedCommandRepository processedCommandRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void whenReserveResourcesSent_thenProcessedAndReplyInOutbox() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String sagaId = UUID.randomUUID().toString();
        String vnfId = "vnf-1";
        String payload = objectMapper.writeValueAsString(Map.of(
                "sagaId", sagaId,
                "vnfId", vnfId,
                "resources", Map.of("vcpu", 2, "memory", 4096)
        ));

        kafkaTemplate.send("vim.commands", messageId, payload).get(5, TimeUnit.SECONDS);

        awaitProcessed(messageId);
        assertThat(processedCommandRepository.existsByMessageId(messageId)).isTrue();

        // Reply should be in outbox (PENDING then forwarded to SENT by OutboxForwarder)
        awaitOutboxReply(sagaId);
    }

    @Test
    void whenDuplicateMessageId_thenProcessedOnlyOnce() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String sagaId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "sagaId", sagaId,
                "vnfId", "vnf-2",
                "resources", Map.of()
        ));

        kafkaTemplate.send("vim.commands", messageId, payload).get(5, TimeUnit.SECONDS);
        awaitProcessed(messageId);
        long countFirst = processedCommandRepository.count();

        // Send same messageId again (simulate duplicate)
        kafkaTemplate.send("vim.commands", messageId, payload).get(5, TimeUnit.SECONDS);
        Thread.sleep(2000);

        // Still only one processed record (idempotency)
        assertThat(processedCommandRepository.count()).isEqualTo(countFirst);
        assertThat(processedCommandRepository.existsByMessageId(messageId)).isTrue();
    }

    @Test
    void whenReleaseResourcesSent_thenProcessed() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String sagaId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "sagaId", sagaId,
                "vnfId", "vnf-3",
                "reason", "step 2 failed"
        ));

        kafkaTemplate.send("vim.commands", messageId, payload).get(5, TimeUnit.SECONDS);

        awaitProcessed(messageId);
        assertThat(processedCommandRepository.existsByMessageId(messageId)).isTrue();
    }

    private void awaitProcessed(String messageId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (processedCommandRepository.existsByMessageId(messageId)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("processed_commands did not contain messageId=" + messageId);
    }

    private void awaitOutboxReply(String sagaId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            var pending = outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                    OutboxStatus.PENDING, java.time.Instant.now());
            var all = outboxRepository.findAll();
            boolean hasReply = all.stream().anyMatch(m -> m.getPayload().contains(sagaId));
            if (hasReply) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Outbox did not contain reply for sagaId=" + sagaId);
    }
}
