package com.telecom.vnfm.lcm.domain;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Holder for the result of an aggregate action that produces domain events.
 * ---------------------------------------------------------------------------
 * DDD ROLE: When an aggregate performs an action (e.g. requestDeployment), it
 * often generates one or more domain events. We do not publish events from
 * inside the entity—that would couple the domain to infrastructure (Kafka/DB).
 * Instead, the entity returns itself plus the events. The application service
 * (LcmService) then (1) persists the aggregate and (2) publishes the events
 * in the same transaction (via the Transactional Outbox). This pattern is
 * described in "Implementing Domain-Driven Design" (Vernon) and used by
 * Eventuate Tram's event-sourcing style APIs.
 *
 * WHY NOT PUBLISH INSIDE THE ENTITY?
 * - Entities should be free of infrastructure concerns. They cannot inject
 *   DomainEventPublisher or know about Kafka. Returning events keeps the
 *   domain pure and testable without a running message broker.
 *
 * @param <R> The aggregate root (e.g. VnfInstance).
 * @param <E> The type of domain events (e.g. InfraDeploymentRequestedEvent).
 */
@Getter
public class ResultWithDomainEvents<R, E> {

    private final R result;
    private final List<E> events;

    public ResultWithDomainEvents(R result, List<E> events) {
        this.result = result;
        this.events = events != null ? List.copyOf(events) : Collections.emptyList();
    }
}
