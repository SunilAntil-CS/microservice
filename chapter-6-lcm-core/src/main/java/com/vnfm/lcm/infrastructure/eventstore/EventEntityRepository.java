package com.vnfm.lcm.infrastructure.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * REVISION – Spring Data JPA Repository
 * -------------------------------------
 * Extends JpaRepository<EventEntity, Long>: Spring Data provides an implementation at runtime.
 * You get save(), findById(), findAll(), delete(), etc. for free. This interface IS a bean
 * (Spring creates a proxy implementing it); we inject it into JdbcEventStore.
 *
 * Method names define the query (query derivation):
 *   findByAggregateIdOrderByVersionAsc  →  SELECT * FROM events WHERE aggregate_id = ? ORDER BY version ASC
 *
 * @Query: Custom JPQL (Java Persistence Query Language). We use the entity class name (EventEntity),
 *         not the table name. COALESCE(MAX(e.version), 0) returns 0 when no rows exist.
 *
 * @Param: Binds the method parameter to the named JPQL parameter :aggregateId.
 */
public interface EventEntityRepository extends JpaRepository<EventEntity, Long> {

    List<EventEntity> findByAggregateIdAndAggregateTypeOrderByVersionAsc(String aggregateId, String aggregateType);

    @Query("SELECT COALESCE(MAX(e.version), 0) FROM EventEntity e WHERE e.aggregateId = :aggregateId AND e.aggregateType = :aggregateType")
    int findMaxVersionByAggregateIdAndAggregateType(@Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType);
}
