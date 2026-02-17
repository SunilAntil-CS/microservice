package com.vnfm.lcm.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for processed_requests table.
 * Used by the idempotency filter to look up cached responses by requestId.
 */
public interface ProcessedRequestRepository extends JpaRepository<ProcessedRequest, Long> {

    Optional<ProcessedRequest> findByRequestId(String requestId);
}
