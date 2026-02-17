package com.telecom.saga.order.domain;

import com.telecom.saga.common.dto.TicketLineItem;
import javax.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ORDER AGGREGATE - Local state in the Order Service database.
 * ---------------------------------------------------------------------------
 * CONCEPT - Local Transaction:
 * Each service has its own database (Database-per-Service). This entity is
 * persisted in the Order Service DB only. We never span a single ACID
 * transaction to the Kitchen DB; instead we use the Saga to coordinate.
 * ---------------------------------------------------------------------------
 * State machine (simplified for Chapter 4 Module 1):
 *   PENDING -> APPROVED (success path)
 *   PENDING -> REJECTED (after compensation)
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which restaurant this order is for. */
    private Long restaurantId;

    /** Customer (or consumer) ID for billing/audit. */
    private Long consumerId;

    /** Saga state: PENDING until all steps complete; then APPROVED or REJECTED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderState state;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLineItem> lineItems = new ArrayList<>();

    public enum OrderState {
        /** Initial state after create; saga not yet completed. */
        PENDING,
        /** All saga steps succeeded. */
        APPROVED,
        /** A step failed and compensations were run. */
        REJECTED
    }

    /**
     * Factory: create a new order in PENDING state (first local transaction of the saga).
     * Accepts shared DTOs (TicketLineItem) so the API layer does not depend on entity types.
     */
    public static Order createOrder(Long restaurantId, Long consumerId, List<TicketLineItem> lineItems) {
        Order order = new Order();
        order.setRestaurantId(restaurantId);
        order.setConsumerId(consumerId);
        order.setState(OrderState.PENDING);
        order.setCreatedAt(Instant.now());
        if (lineItems != null) {
            for (TicketLineItem dto : lineItems) {
                order.addLineItem(OrderLineItem.from(dto));
            }
        }
        return order;
    }

    private void addLineItem(OrderLineItem item) {
        lineItems.add(item);
        item.setOrder(this);
    }

    public void approve() {
        this.state = OrderState.APPROVED;
    }

    public void reject() {
        this.state = OrderState.REJECTED;
    }
}
