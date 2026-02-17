package com.telecom.fms.repository;

import com.telecom.fms.model.ReceivedMessage;
import com.telecom.fms.model.ReceivedMessageId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the idempotency table. We only need save() — the PK constraint
 * causes duplicate key exception when the same (consumer_id, message_id) is inserted again.
 */
public interface ReceivedMessageRepository extends JpaRepository<ReceivedMessage, ReceivedMessageId> {
}
