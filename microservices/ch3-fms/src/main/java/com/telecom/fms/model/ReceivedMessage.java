package com.telecom.fms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * MODULE 4: The "memory" table for idempotency. We remember every message_id we have processed.
 *
 * CONCEPT: PRIMARY KEY (consumer_id, message_id). Insert before business logic in the
 * same transaction. If the insert fails with duplicate key, we have already processed
 * this message → return and do nothing. If it succeeds, we proceed to create the ticket.
 * The database is the only source of truth; no Redis/cache for this check (race condition).
 *
 * @IdClass(ReceivedMessageId.class): JPA composite key. Both fields are part of the PK.
 */
@Entity
@Table(name = "received_messages")
@IdClass(ReceivedMessageId.class)
public class ReceivedMessage {

    @Id
    @Column(name = "consumer_id", nullable = false, length = 255)
    private String consumerId;

    @Id
    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "creation_time")
    private Long creationTime;

    public ReceivedMessage() {}

    public ReceivedMessage(String consumerId, String messageId) {
        this.consumerId = consumerId;
        this.messageId = messageId;
        this.creationTime = System.currentTimeMillis();
    }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Long getCreationTime() { return creationTime; }
    public void setCreationTime(Long creationTime) { this.creationTime = creationTime; }
}
