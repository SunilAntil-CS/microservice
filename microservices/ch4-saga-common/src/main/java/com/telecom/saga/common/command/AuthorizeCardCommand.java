package com.telecom.saga.common.command;

import io.eventuate.tram.commands.common.Command;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * COMMAND: Authorize customer payment card (Step 2 of the Saga).
 * ---------------------------------------------------------------------------
 * SAGA ROLE - Pivot Transaction:
 * The "pivot" is the last compensatable step before the point of no return.
 * If AuthorizeCard succeeds, we proceed to Approve Order. If it fails (e.g.
 * CardExpiredReply), the saga runs compensations (e.g. Cancel Ticket).
 * In a strict definition, the pivot has no compensation (the decision is
 * committed); here we treat "authorize" as the step that can fail and
 * trigger rollback of prior steps (Kitchen ticket).
 * ---------------------------------------------------------------------------
 * PRODUCTION: In Telecom domains this could be "ReserveCredit" or
 * "ValidateSubscription" — same pattern: forward command, success/failure reply.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizeCardCommand implements Command {

    /** Order ID for correlation and idempotency. */
    private Long orderId;

    /** Consumer/customer ID who placed the order. */
    private Long consumerId;

    /** Total amount to authorize (e.g. order total). */
    private BigDecimal total;

    /** Payment token or card reference (opaque to orchestrator). */
    private String paymentToken;
}
