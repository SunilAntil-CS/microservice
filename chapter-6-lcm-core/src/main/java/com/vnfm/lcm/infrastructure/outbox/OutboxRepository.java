package com.vnfm.lcm.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * STUDY NOTE – Spring Data JPA repository for outbox table
 * --------------------------------------------------------
 * Query derivation: findByNameAndStatusOrderBy... generates
 *   SELECT * FROM outbox WHERE status = ? AND next_retry_at <= ? ORDER BY next_retry_at ASC
 * We use this in OutboxForwarder to fetch only messages that are due for (re)send.
 */
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Fetch all PENDING messages whose next retry time has passed.
     * Ordered by next_retry_at so we process oldest first.
     */
    List<OutboxMessage> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            OutboxStatus status,
            Instant now
    );
}
