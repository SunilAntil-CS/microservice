package com.telecom.vnfm.lcm.idempotency;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * IDEMPOTENCY SHIELD (Bouncer) — Phase 5 reply consumer.
 * ---------------------------------------------------------------------------
 * Infrastructure-only entity for duplicate detection. Stores the Kafka message_id
 * (set by VIM OutboxRelay) that we have already processed. No business logic;
 * the PK constraint guarantees at most one row per message_id, so concurrent
 * redelivery is safely rejected. processedAt is for auditing and optional TTL.
 */
@Entity
@Table(name = "processed_message")
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id", length = 128, nullable = false, updatable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @SuppressWarnings("unused")
    protected ProcessedMessageEntity() {
    }

    public static ProcessedMessageEntity of(String messageId) {
        ProcessedMessageEntity e = new ProcessedMessageEntity();
        e.messageId = messageId != null && messageId.length() > 128
                ? messageId.substring(0, 128)
                : messageId;
        e.processedAt = LocalDateTime.now();
        return e;
    }

    public String getMessageId() {
        return messageId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
