package com.telecom.saga.common.command;

import com.telecom.saga.common.dto.TicketLineItem;
import io.eventuate.tram.commands.common.Command;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * COMMAND: Create Kitchen Ticket (Step 1 of the Saga).
 * ---------------------------------------------------------------------------
 * CONCEPT - Compensatable Transaction:
 * This is the "forward" action. The Saga Orchestrator (Order Service) sends this
 * to the Kitchen Service. If a later step (e.g. Accounting) fails, we must
 * execute the compensating transaction: CancelCreateTicketCommand.
 * ---------------------------------------------------------------------------
 * PRODUCTION NOTE: In a messaging setup (Eventuate Tram / Kafka), this would
 * be serialized to JSON and sent to a channel like "kitchenService". Here we
 * use the same DTO for REST so the contract is identical.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketCommand implements Command {

    /** Order ID from Order Service; used for correlation and compensation lookup. */
    private Long orderId;

    /** Restaurant that will prepare the order. */
    private Long restaurantId;

    /** Line items (menu item id + quantity) for the ticket. */
    private List<TicketLineItem> lineItems;
}
