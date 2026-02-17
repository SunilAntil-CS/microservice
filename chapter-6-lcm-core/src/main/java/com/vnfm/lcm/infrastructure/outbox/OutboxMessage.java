package com.vnfm.lcm.infrastructure.outbox;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * STUDY NOTE – Outbox table entity (Transactional Outbox Pattern)
 * ---------------------------------------------------------------
 * The outbox table stores messages that must be sent to external systems (e.g. Kafka)
 * in the same transaction as the business write. This ensures at-least-once delivery:
 * if we commit the transaction, the message is in the outbox; a separate process
 * (OutboxForwarder) then publishes it. If the app crashes after commit but before
 * Kafka send, the message is still in the DB and will be retried.
 *
 * JPA annotations (recap):
 * - @Entity: maps this class to table "outbox"
 * - @Table(indexes): idx on (status, next_retry_at) so the forwarder can quickly find
 *   PENDING messages due for retry
 * - @Enumerated(STRING): store enum as "PENDING"/"SENT" in DB, not ordinal (robust to reordering)
 */
@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_status_next_retry", columnList = "status, next_retry_at")
})
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique id for the message (e.g. UUID); used for idempotency at consumer. */
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    /** Logical destination (e.g. "vim.manager"); mapped to Kafka topic by the publisher. */
    @Column(name = "destination", nullable = false, length = 128)
    private String destination;

    /** Type of message (e.g. "InstantiateVnfCommand") for consumer deserialization. */
    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

    /** JSON payload; stored as string so we stay transport-agnostic in the entity. */
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When to attempt the next publish (for backoff: first attempt immediately, then delayed). */
    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    /** Last error message if publish failed (for debugging / monitoring). */
    @Column(name = "last_error", length = 2048)
    private String lastError;

    @SuppressWarnings("unused")
    public OutboxMessage() {
        // JPA no-arg constructor
    }

    public OutboxMessage(String messageId, String destination, String messageType, String payload) {
        this.messageId = messageId;
        this.destination = destination;
        this.messageType = messageType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.nextRetryAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
