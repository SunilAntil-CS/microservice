package com.vnfm.vimmanager.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Idempotency record: stores message_id of consumed commands so we skip duplicates.
 */
@Entity
@Table(name = "processed_commands", indexes = {
        @Index(name = "idx_processed_commands_message_id", columnList = "message_id", unique = true)
})
public class ProcessedCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @SuppressWarnings("unused")
    public ProcessedCommand() {
    }

    public ProcessedCommand(String messageId) {
        this.messageId = messageId;
        this.processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
