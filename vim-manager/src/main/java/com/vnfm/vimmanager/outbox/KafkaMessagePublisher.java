package com.vnfm.vimmanager.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes outbox messages to Kafka. Destination (e.g. vim.replies) is used as topic name.
 */
@Component
public class KafkaMessagePublisher implements MessagePublisher {

    private static final Map<String, String> DESTINATION_TO_TOPIC = Map.of(
            "vim.replies", "vim.replies"
    );

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxMessage message) {
        String topic = DESTINATION_TO_TOPIC.getOrDefault(message.getDestination(), message.getDestination());
        try {
            kafkaTemplate.send(topic, message.getMessageId(), message.getPayload()).get();
        } catch (Exception e) {
            throw new PublishException("Failed to publish message " + message.getMessageId() + " to " + topic, e);
        }
    }
}
