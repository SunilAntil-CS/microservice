package com.telecom.vnfm.lcm.outbox;

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

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("DELETE FROM OutboxMessageEntity o WHERE o.id = :id")
    void deleteMessageAfterPublish(@Param("id") String id);
}
