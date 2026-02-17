package com.telecom.vnfm.vim.idempotency;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * IDEMPOTENCY SHIELD (Bouncer) - Infrastructure-only record for duplicate detection.
 * -------------------------------------------------------------------------------
 * This entity lives outside the business domain. It has no relationship to
 * CloudDeployment or any other aggregate. Its sole purpose is to answer:
 * "Have we already processed a message with this id?"
 *
 * DESIGN RATIONALE:
 * - Keeps messaging concerns (Kafka message_id) out of the CloudDeployment
 *   aggregate. The domain model stays pure: deploymentId, vnfId, status only.
 * - Using the database as the single source of truth: UNIQUE on message_id
 *   (primary key) guarantees that even under concurrent delivery (e.g. consumer
 *   rebalance), only one consumer can successfully insert a given message id.
 * - processedAt is for auditing and optional TTL/cleanup; the idempotency
 *   guarantee comes from the PK constraint, not from this field.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {

    /**
     * Idempotency key: the Kafka message_id (or "id" header). Must be provided
     * by the message pipeline; we never generate a fallback (see VimEventHandler).
     */
    @Id
    @Column(name = "message_id", length = 128, nullable = false, updatable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @SuppressWarnings("unused")
    protected ProcessedMessageEntity() {
    }

    public ProcessedMessageEntity(String messageId) {
        this.messageId = messageId != null && messageId.length() > 128
                ? messageId.substring(0, 128)
                : messageId;
        this.processedAt = LocalDateTime.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
