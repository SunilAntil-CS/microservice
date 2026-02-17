package com.telecom.saga.kitchen.domain;

import com.telecom.saga.common.dto.TicketLineItem;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "ticket_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLineItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String menuItemId;
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    public static TicketLineItemEntity from(TicketLineItem dto) {
        return TicketLineItemEntity.builder()
                .menuItemId(dto.getMenuItemId())
                .quantity(dto.getQuantity())
                .build();
    }
}
