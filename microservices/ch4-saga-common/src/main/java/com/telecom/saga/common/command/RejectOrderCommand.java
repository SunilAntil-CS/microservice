package com.telecom.saga.common.command;

import io.eventuate.tram.commands.common.Command;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * COMMAND: Reject an order (compensation when saga fails).
 * ---------------------------------------------------------------------------
 * SAGA ROLE - Compensation for "Order Created":
 * The first local transaction in the saga is "create order (PENDING)". If any
 * later step fails, we compensate by marking the order REJECTED. The orchestrator
 * can send this command to itself (order-service channel) so that a single
 * handler updates the order state; this keeps the saga definition declarative.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectOrderCommand implements Command {

    private Long orderId;
}
