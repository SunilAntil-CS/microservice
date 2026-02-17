package com.telecom.vnfm.lcm.outbox;

import java.util.List;

/**
 * DOMAIN EVENT PUBLISHER (represents Eventuate Tram).
 * ---------------------------------------------------------------------------
 * In production, this interface is implemented by Eventuate Tram's
 * io.eventuate.tram.events.publisher.DomainEventPublisher. The key guarantee
 * is TRANSACTIONAL OUTBOX: publish() does not send to Kafka directly. Instead,
 * it writes the event(s) to an outbox table (e.g. "message") in the SAME
 * database transaction as the aggregate persist. So either both the aggregate
 * row and the outbox row(s) are committed, or neither. A separate process
 * (CDC like Debezium, or a polling relay) then reads the outbox and publishes
 * to Kafka. This eliminates the "dual write" problem (DB commit vs Kafka send
 * can fail independently) and ensures at-least-once delivery to consumers.
 *
 * WHY DB-BASED OUTBOX ON THE MANAGEMENT PLANE?
 * - The VNFM operates at low TPS (10–50 during scaling). Consistency and
 *   reliability are more important than microsecond latency. A relational DB
 *   is acceptable for the outbox table. See INTERVIEW_PREP_VNFM.md.
 *
 * @param <E> Type of domain event (e.g. InfraDeploymentRequestedEvent).
 */
public interface DomainEventPublisher<E> {

    /**
     * Publishes domain events atomically with the current transaction by
     * writing to the outbox table. The aggregateType and aggregateId are
     * used by Eventuate for routing and idempotency; our implementation
     * stores them in headers or destination for the relay.
     */
    void publish(Class<?> aggregateType, Object aggregateId, List<E> events);
}
