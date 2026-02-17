package com.telecom.saga.common.command;

import io.eventuate.tram.commands.common.Command;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * COMMAND: Cancel (undo) a previously created Kitchen Ticket.
 * ---------------------------------------------------------------------------
 * CONCEPT - Compensating Transaction:
 * When a step after "Create Ticket" fails (e.g. credit authorization fails),
 * the orchestrator runs compensations in reverse order. This command is the
 * "undo" for CreateTicketCommand. The orchestrator must have saved the
 * ticketId from CreateTicketReply to send it here.
 * ---------------------------------------------------------------------------
 * RULE: Every compensatable forward action must have a defined compensating
 * action. No automatic rollback across services—we implement "undo" explicitly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelCreateTicketCommand implements Command {

    /** ID of the ticket to cancel (returned earlier in CreateTicketReply). */
    private Long ticketId;

    /** Order ID for logging and idempotency context. */
    private Long orderId;
}
