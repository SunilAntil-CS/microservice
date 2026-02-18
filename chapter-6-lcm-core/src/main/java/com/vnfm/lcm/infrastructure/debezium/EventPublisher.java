package com.vnfm.lcm.infrastructure.debezium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards CDC change events to Kafka using KafkaTemplate.
 * Used by DebeziumListener to publish each captured change to the appropriate topic.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a change event to the given Kafka topic.
     *
     * @param topic Kafka topic
     * @param key   message key (e.g. event_id or message_id for ordering)
     * @param value JSON payload (CDC envelope or payload)
     */
    public void publish(String topic, String key, String value) {
        try {
            kafkaTemplate.send(topic, key, value).get();
            log.debug("Published CDC event to topic={} key={}", topic, key);
        } catch (Exception e) {
            log.error("Failed to publish CDC event to topic={} key={}: {}", topic, key, e.getMessage());
            throw new RuntimeException("Failed to publish to Kafka: " + e.getMessage(), e);
        }
    }
}
