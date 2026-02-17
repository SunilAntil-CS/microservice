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
        for (OutboxMessageEntity message : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        message.getDestination(), null, message.getId(), message.getPayload());
                record.headers().add(new RecordHeader("message_id", message.getId().getBytes(StandardCharsets.UTF_8)));
                kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
                outboxMessageRepository.deleteMessageAfterPublish(message.getId());
            } catch (Exception e) {
                log.warn("Outbox relay failed for messageId={}: {}", message.getId(), e.getMessage());
            }
        }
    }
}
