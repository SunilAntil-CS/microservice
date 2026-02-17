package com.telecom.saga.kitchen.domain;

import com.telecom.saga.common.dto.TicketLineItem;
import javax.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TICKET AGGREGATE - Kitchen's local entity (Database-per-Service).
 * ---------------------------------------------------------------------------
 * CONCEPT: The Kitchen Service has its own database. This entity is persisted
 * only here. The Order Service never touches this table; it sends commands
 * and receives replies. When the orchestrator runs compensation, it sends
 * CancelCreateTicketCommand; we update state to CANCELLED in a new local TX.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketState state;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketLineItemEntity> lineItems = new ArrayList<>();

    public enum TicketState {
        CREATED,
        CANCELLED
    }

    /**
     * Factory: create a new ticket in CREATED state (forward step).
     */
    public static Ticket create(Long orderId, Long restaurantId, List<TicketLineItem> lineItems) {
        Ticket ticket = new Ticket();
        ticket.setOrderId(orderId);
        ticket.setRestaurantId(restaurantId);
        ticket.setState(TicketState.CREATED);
        if (lineItems != null) {
            for (TicketLineItem dto : lineItems) {
                ticket.addLineItem(TicketLineItemEntity.from(dto));
            }
        }
        return ticket;
    }

    private void addLineItem(TicketLineItemEntity item) {
        lineItems.add(item);
        item.setTicket(this);
    }

    /** Compensating action: mark ticket as cancelled. */
    public void cancel() {
        this.state = TicketState.CANCELLED;
    }
}
