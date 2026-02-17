package com.telecom.vnfm.vim.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the Idempotency Shield (processed message records).
 * Used by VimEventHandler to enforce the "Bouncer" pattern: only one
 * successful insert per message_id; duplicates are rejected by the PK constraint.
 */
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, String> {
}
