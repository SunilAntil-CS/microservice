package com.telecom.vnfm.lcm.idempotency;

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

    public static ProcessedMessageEntity of(String messageId) {
        ProcessedMessageEntity e = new ProcessedMessageEntity();
        e.messageId = messageId != null && messageId.length() > 128 ? messageId.substring(0, 128) : messageId;
        e.processedAt = LocalDateTime.now();
        return e;
    }

    public String getMessageId() { return messageId; }
}
