package com.vnfm.lcm.infrastructure.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * REVISION – Spring Data JPA query derivation
 * --------------------------------------------
 * findTop1ByAggregateIdOrderByVersionDesc:
 *   findTop1  = limit 1 (return at most one result)
 *   ByAggregateId  = WHERE aggregate_id = ?
 *   OrderByVersionDesc  = ORDER BY version DESC
 * So we get the latest snapshot (highest version) for the aggregate. Returns Optional
 * because there might be no snapshot yet.
 */
public interface SnapshotEntityRepository extends JpaRepository<SnapshotEntity, Long> {

    Optional<SnapshotEntity> findTop1ByAggregateIdOrderByVersionDesc(String aggregateId);
}
