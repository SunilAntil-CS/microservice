package com.vnfm.lcm.infrastructure.eventstore;

import com.vnfm.lcm.domain.DomainEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REVISION – Using JPA repositories and transactions
 * ---------------------------------------------------
 * @Repository: Stereotype for persistence. Spring treats it as a bean and can translate
 *              persistence exceptions (e.g. JPA → DataAccessException).
 *
 * We inject the repository interfaces (they are beans). We create entity instances
 * ourselves (new EventEntity(...)) and pass them to repository.save(entity).
 *
 * @Transactional: Runs the method inside a DB transaction. If the method completes normally,
 *                 the transaction commits (changes are persisted). If an exception is thrown,
 *                 the transaction rolls back. readOnly = true hints that we only read (no
 *                 writes), which can allow optimizations (e.g. no flush before the query).
 */
@Repository
public class JdbcEventStore implements EventStore {

    private final EventEntityRepository eventRepository;
    private final SnapshotEntityRepository snapshotRepository;
    private final DomainEventSerializer serializer;

    public JdbcEventStore(EventEntityRepository eventRepository,
                          SnapshotEntityRepository snapshotRepository,
                          DomainEventSerializer serializer) {
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.serializer = serializer;
    }

    @Override
    @Transactional
    public void saveEvents(UUID aggregateId, String aggregateType, List<DomainEvent> events, int expectedVersion) {
        if (events == null || events.isEmpty()) {
            return;
        }
        String aggregateIdStr = aggregateId.toString();
        String type = aggregateType != null ? aggregateType : EventStore.AGGREGATE_TYPE_VNF;
        int maxVersion = eventRepository.findMaxVersionByAggregateIdAndAggregateType(aggregateIdStr, type);
        if (maxVersion != expectedVersion) {
            throw new OptimisticLockingException(aggregateIdStr, expectedVersion, maxVersion);
        }
        int nextVersion = expectedVersion + 1;
        for (DomainEvent event : events) {
            if (event.getVersion() != nextVersion) {
                throw new IllegalArgumentException(
                        "Event version " + event.getVersion() + " must be " + nextVersion);
            }
            EventEntity entity = new EventEntity(
                    event.getEventId(),
                    aggregateIdStr,
                    type,
                    event.getVersion(),
                    event.getClass().getSimpleName(),
                    serializer.serialize(event),
                    event.getTimestamp()
            );
            eventRepository.save(entity);
            nextVersion++;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> loadEvents(UUID aggregateId, String aggregateType) {
        String aggregateIdStr = aggregateId.toString();
        String type = aggregateType != null ? aggregateType : EventStore.AGGREGATE_TYPE_VNF;
        return eventRepository.findByAggregateIdAndAggregateTypeOrderByVersionAsc(aggregateIdStr, type).stream()
                .map(e -> serializer.deserialize(e.getPayload(), type))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snapshot> getLatestSnapshot(UUID aggregateId, String aggregateType) {
        String aggregateIdStr = aggregateId.toString();
        String type = aggregateType != null ? aggregateType : EventStore.AGGREGATE_TYPE_VNF;
        return snapshotRepository.findTop1ByAggregateIdOrderByVersionDesc(aggregateIdStr)
                .map(s -> new Snapshot(UUID.fromString(s.getAggregateId()), s.getVersion(), s.getPayload()));
    }
}
