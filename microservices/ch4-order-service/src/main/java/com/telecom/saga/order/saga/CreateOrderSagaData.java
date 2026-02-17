package com.telecom.saga.order.saga;

import com.telecom.saga.common.dto.TicketLineItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * SAGA STATE - Data carried through the saga steps (persisted in saga_instance by Eventuate Tram).
 * ---------------------------------------------------------------------------
 * CONCEPT: The orchestrator must remember (a) the order id, (b) data needed
 * to invoke participants (restaurantId, lineItems, orderTotal), and (c) data
 * returned by participants (e.g. ticketId) for compensation. Eventuate Tram
 * serializes this to saga_instance.saga_data_json and restores it on reply.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderSagaData {

    private Long orderId;
    private Long restaurantId;
    private Long consumerId;
    private List<TicketLineItem> lineItems;

    /** Order total for card authorization (Pivot step). */
    private BigDecimal orderTotal;

    /** Filled when Kitchen replies with CreateTicketReply; needed for compensation. */
    private Long ticketId;
}
