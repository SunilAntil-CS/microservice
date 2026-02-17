package com.telecom.vnfm.lcm.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox repository for the LCM service.
 * ---------------------------------------------------------------------------
 * HARD-DELETE RELAY STRATEGY: After the relay successfully publishes a row to
 * Kafka (kafkaTemplate.send().get()), we delete the row instead of marking
 * published=1. This prevents table bloat in production—only in-flight messages
 * remain in the table. See OutboxRelay and INTERVIEW_PREP_VNFM.md.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {

    List<OutboxMessageEntity> findByPublishedOrderByIdAsc(int published, Pageable pageable);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("DELETE FROM OutboxMessageEntity o WHERE o.id = :id")
    void deleteMessageAfterPublish(@Param("id") String id);
}
