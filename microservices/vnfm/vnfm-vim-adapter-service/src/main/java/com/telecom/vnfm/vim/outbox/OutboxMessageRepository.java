package com.telecom.vnfm.vim.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {

    List<OutboxMessageEntity> findByPublishedOrderByIdAsc(int published, Pageable pageable);

    /**
     * Deletes a single outbox row after the relay has successfully published it to Kafka.
     * Runs in its own transaction (REQUIRES_NEW) so the delete commits independently of
     * the caller. Called by OutboxRelay only after kafkaTemplate.send(...).get() returns.
     *
     * WHY DELETE INSTEAD OF UPDATE (published=1)?
     * In high-TPS telecom environments, marking rows as "published" would leave millions
     * of rows in the table. Indexes and table scans degrade; retention/cleanup becomes
     * a separate operational burden. Deleting after successful publish keeps the outbox
     * table small and fast—only in-flight (unpublished) rows remain.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("DELETE FROM OutboxMessageEntity o WHERE o.id = :id")
    void deleteMessageAfterPublish(@Param("id") String id);
}
