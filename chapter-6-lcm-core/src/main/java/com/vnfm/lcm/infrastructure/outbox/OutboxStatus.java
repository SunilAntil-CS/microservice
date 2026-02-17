package com.vnfm.lcm.infrastructure.outbox;

/**
 * STUDY NOTE – Outbox message status
 * ----------------------------------
 * PENDING: Message is written to the outbox table and waiting to be published to Kafka.
 *          The forwarder will pick it up when nextRetryAt <= now().
 * SENT:    Message was successfully published. No further retries.
 *          (Optional: could add FAILED for max retries exceeded; this design keeps retrying.)
 */
public enum OutboxStatus {
    PENDING,
    SENT
}
