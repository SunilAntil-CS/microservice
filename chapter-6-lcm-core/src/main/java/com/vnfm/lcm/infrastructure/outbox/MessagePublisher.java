package com.vnfm.lcm.infrastructure.outbox;

/**
 * STUDY NOTE – Port for publishing outbox messages
 * ------------------------------------------------
 * Interface (port) so we can swap implementations (Kafka, REST, etc.) and test
 * the forwarder with a mock. KafkaMessagePublisher is the adapter that sends
 * to Kafka based on the message's destination.
 */
public interface MessagePublisher {

    /**
     * Publish the given outbox message to the appropriate destination (e.g. Kafka topic).
     *
     * @param message the outbox message (destination, payload, etc.)
     * @throws PublishException if the publish fails (e.g. broker unavailable)
     */
    void publish(OutboxMessage message);
}
