package com.telecom.saga.common.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REPLY: Kitchen Service response after creating a ticket.
 * ---------------------------------------------------------------------------
 * CONCEPT: The orchestrator must store the ticketId to perform compensation
 * later. If we didn't return it, we could not call CancelCreateTicketCommand
 * with the correct ID. All replies that support compensation must carry the
 * identifiers needed for the compensating command.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketReply {

    /** ID of the created ticket; required for cancel (compensation). */
    private Long ticketId;

    /** Optional: order ID echo for correlation. */
    private Long orderId;
}
