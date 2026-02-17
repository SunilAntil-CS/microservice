package com.telecom.saga.order.domain;

import com.telecom.saga.common.dto.TicketLineItem;
import javax.persistence.*;
import lombok.*;

/**
 * Line item for an order (menu item + quantity).
 * Maps to the shared TicketLineItem DTO when sending CreateTicketCommand.
 */
@Entity
@Table(name = "order_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String menuItemId;
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    public static OrderLineItem from(TicketLineItem dto) {
        return OrderLineItem.builder()
                .menuItemId(dto.getMenuItemId())
                .quantity(dto.getQuantity())
                .build();
    }

    public TicketLineItem toTicketLineItem() {
        return TicketLineItem.builder()
                .menuItemId(menuItemId)
                .quantity(quantity)
                .build();
    }
}
