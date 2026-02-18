package com.vnfm.vimmanager.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            OutboxStatus status,
            Instant now
    );
}
