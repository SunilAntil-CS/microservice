package com.telecom.vnfm.vim.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the CloudDeployment aggregate (VIM database only).
 * Idempotency is handled by the dedicated ProcessedMessageRepository.
 */
public interface CloudDeploymentRepository extends JpaRepository<CloudDeployment, String> {
}
