package com.telecom.fms.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for ReceivedMessage (JPA @IdClass).
 * Must be Serializable, have equals/hashCode, and a no-arg constructor.
 *
 * CONCEPT: The DB enforces UNIQUE(consumer_id, message_id). Two consumers can
 * process the same message_id (different consumer_id); one consumer must not
 * process the same message_id twice.
 */
public class ReceivedMessageId implements Serializable {

    private String consumerId;
    private String messageId;

    public ReceivedMessageId() {}

    public ReceivedMessageId(String consumerId, String messageId) {
        this.consumerId = consumerId;
        this.messageId = messageId;
    }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReceivedMessageId that = (ReceivedMessageId) o;
        return Objects.equals(consumerId, that.consumerId) && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId, messageId);
    }
}
