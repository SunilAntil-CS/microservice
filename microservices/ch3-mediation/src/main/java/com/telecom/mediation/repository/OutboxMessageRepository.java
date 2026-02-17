package com.telecom.mediation.repository;

import com.telecom.mediation.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for the outbox (message) table. The relay queries unpublished messages.
 *
 * findByPublishedOrderByCreationTimeAsc(int published): Spring Data derives the
 * query from the method name. "findBy" + "Published" (field) + "OrderBy" +
 * "CreationTime" + "Asc". Equivalent to:
 *   SELECT * FROM message WHERE published = ? ORDER BY creation_time ASC
 * We use this to process messages in order and avoid blocking on one slow send.
 *
 * In production you might add limit (e.g. Pageable) and/or mark as "in progress"
 * to avoid two relay instances processing the same row.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {

    List<OutboxMessage> findByPublishedOrderByCreationTimeAsc(int published);
}
