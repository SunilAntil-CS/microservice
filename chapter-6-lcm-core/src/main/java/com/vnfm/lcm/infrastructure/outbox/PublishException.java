package com.vnfm.lcm.infrastructure.outbox;

/**
 * Thrown when publishing an outbox message fails (e.g. Kafka broker unavailable).
 * The forwarder catches this to update retry count and nextRetryAt.
 */
public class PublishException extends RuntimeException {

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public PublishException(String message) {
        super(message);
    }
}
