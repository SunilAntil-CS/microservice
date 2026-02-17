package com.vnfm.lcm.infrastructure.eventstore;

import com.vnfm.lcm.domain.DomainEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists and loads domain events per aggregate with optimistic locking.
 * aggregateType distinguishes VNF vs OP_OCC (operation occurrence) streams in the same table.
 */
public interface EventStore {

    String AGGREGATE_TYPE_VNF = "VNF";
    String AGGREGATE_TYPE_OP_OCC = "OP_OCC";

    /**
     * Appends events for the given aggregate. Fails if current max version for the aggregate
     * is not equal to expectedVersion (optimistic lock).
     *
     * @param aggregateId     the aggregate id (VNF id or operation occurrence id)
     * @param aggregateType   "VNF" or "OP_OCC"
     * @param events          new events to append (versions must be expectedVersion+1, +2, ...)
     * @param expectedVersion the version the aggregate is expected to be at before these events
     * @throws OptimisticLockingException if max version in store != expectedVersion
     */
    void saveEvents(UUID aggregateId, String aggregateType, List<DomainEvent> events, int expectedVersion);

    /**
     * Loads all events for the aggregate in version order (ascending).
     */
    List<DomainEvent> loadEvents(UUID aggregateId, String aggregateType);

    /**
     * Returns the latest snapshot for the aggregate, if any (used to speed up rebuild).
     */
    Optional<Snapshot> getLatestSnapshot(UUID aggregateId, String aggregateType);
}
