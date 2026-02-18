package com.cqrs.policyquery.infrastructure.kafka;

import com.cqrs.policyquery.application.PolicyEventIndexer;
import com.cqrs.policyquery.config.PolicyQueryProperties;
import com.cqrs.policyquery.domain.PolicyEventPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes policy events from Kafka (published by Debezium from outbox).
 * Indexes idempotently in Elasticsearch; on permanent failure sends to DLQ and acks to skip.
 */
@Component
public class PolicyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PolicyEventConsumer.class);

    private final PolicyEventIndexer indexer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PolicyQueryProperties properties;
    private final ObjectMapper objectMapper;

    public PolicyEventConsumer(PolicyEventIndexer indexer,
                               KafkaTemplate<String, String> kafkaTemplate,
                               PolicyQueryProperties properties,
                               ObjectMapper objectMapper) {
        this.indexer = indexer;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${policy-query.kafka.topic:policy-events}",
            groupId = "${spring.kafka.consumer.group-id:policy-query-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPolicyEvent(ConsumerRecord<String, String> record,
                              Acknowledgment ack,
                              @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        String eventId = key != null ? key : "unknown";
        MDC.put("eventId", eventId);
        try {
            PolicyEventPayload event = objectMapper.readValue(record.value(), PolicyEventPayload.class);
            MDC.put("subscriberId", event.subscriberId());

            try {
                indexer.index(event);
            } catch (Exception e) {
                log.error("Indexing failed (sending to DLQ) eventId={} error={}", eventId, e.getMessage(), e);
                sendToDlq(record, e.getMessage());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Deserialization or processing failed (sending to DLQ) eventId={} error={}", eventId, e.getMessage(), e);
            sendToDlq(record, e.getMessage());
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    private void sendToDlq(ConsumerRecord<String, String> record, String errorMessage) {
        String dlqTopic = properties.getKafka().getDlqTopic();
        try {
            kafkaTemplate.send(dlqTopic, record.key(), record.value()).get();
            log.info("Sent to DLQ topic={} key={}", dlqTopic, record.key());
        } catch (Exception ex) {
            log.error("Failed to send to DLQ topic={} key={}", dlqTopic, record.key(), ex);
        }
    }
}
