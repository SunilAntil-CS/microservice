package com.telecom.vnfm.lcm.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the LCM Idempotency Shield. Used by LcmEventHandler to
 * enforce at-most-once processing of InfraDeployedReplyEvent (Phase 5).
 */
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, String> {
}
