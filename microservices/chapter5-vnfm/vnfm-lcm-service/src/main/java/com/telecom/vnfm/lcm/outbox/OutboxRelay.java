package com.telecom.vnfm.lcm.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hard-Delete Relay: poll outbox, send to Kafka with .get() for sync ACK, then DELETE row.
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
        if (!relayEnabled) return;
        List<OutboxMessageEntity> batch = outboxMessageRepository
                .findByPublishedOrderByIdAsc(0, PageRequest.of(0, batchSize));
        for (OutboxMessageEntity message : batch) {
            try {
                sendToKafka(message);
                outboxMessageRepository.deleteMessageAfterPublish(message.getId());
            } catch (Exception e) {
                log.warn("Outbox relay failed for messageId={}, will retry: {}", message.getId(), e.getMessage());
            }
        }
    }

    private void sendToKafka(OutboxMessageEntity message) throws ExecutionException, InterruptedException, TimeoutException {
        String topic = message.getDestination();
        String key = message.getId();
        String value = message.getPayload();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, value);
        record.headers().add(new RecordHeader("message_id", message.getId().getBytes(StandardCharsets.UTF_8)));
        SendResult<String, String> result = kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        if (result == null) throw new IllegalStateException("Send result null for " + key);
    }
}
