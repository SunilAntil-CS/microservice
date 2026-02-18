package com.vnfm.vimmanager.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_status_next_retry", columnList = "status, next_retry_at")
})
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    @Column(name = "destination", nullable = false, length = 128)
    private String destination;

    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

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

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 2048)
    private String lastError;

    @SuppressWarnings("unused")
    public OutboxMessage() {
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

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getDestination() { return destination; }
    public String getMessageType() { return messageType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
