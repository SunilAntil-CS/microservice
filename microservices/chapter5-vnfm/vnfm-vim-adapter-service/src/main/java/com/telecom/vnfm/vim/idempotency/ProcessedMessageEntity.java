package com.telecom.vnfm.vim.idempotency;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id", length = 128, nullable = false, updatable = false)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedMessageEntity() {
    }

    public ProcessedMessageEntity(String eventId) {
        this.messageId = eventId != null && eventId.length() > 128 ? eventId.substring(0, 128) : eventId;
        this.processedAt = LocalDateTime.now();
    }

    public String getMessageId() { return messageId; }
}
