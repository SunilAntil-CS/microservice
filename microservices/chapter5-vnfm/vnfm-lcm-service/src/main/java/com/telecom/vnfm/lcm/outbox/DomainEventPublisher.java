package com.telecom.vnfm.lcm.outbox;

import java.util.List;

/** Writes events to outbox in same TX as aggregate (Transactional Outbox). */
public interface DomainEventPublisher<E> {
    void publish(Class<?> aggregateType, Object aggregateId, List<E> events);
}
