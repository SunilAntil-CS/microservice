package com.vnfm.lcm.infrastructure.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * STUDY NOTE – Kafka adapter for outbox publishing
 * ------------------------------------------------
 * Maps logical "destination" (e.g. vim.manager) to a Kafka topic (e.g. vim.commands).
 * Uses KafkaTemplate which is configured with bootstrap servers and serializers in
 * application.yml. We send key = messageId (for partitioning/ordering) and value = payload (JSON).
 */
@Component
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Destination -> topic mapping (e.g. vim.manager -> vim.commands). Can be externalized to config. */
    private static final java.util.Map<String, String> DESTINATION_TO_TOPIC = java.util.Map.of(
            "vim.manager", "vim.commands"
    );

    public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxMessage message) {
        String topic = resolveTopic(message.getDestination());
        try {
            // STUDY NOTE: send().get() blocks until ack; in production consider fire-and-forget
            // or callback to avoid blocking the forwarder thread. get() ensures we throw on failure.
            kafkaTemplate.send(topic, message.getMessageId(), message.getPayload()).get();
        } catch (Exception e) {
            throw new PublishException("Failed to publish message " + message.getMessageId() + " to " + topic, e);
        }
    }

    /**
     * Resolve Kafka topic from logical destination.
     * Default: use destination as topic name if not in map (e.g. "vim.manager" -> "vim.commands").
     */
    private String resolveTopic(String destination) {
        return DESTINATION_TO_TOPIC.getOrDefault(destination, destination);
    }
}
