package com.telecom.saga.common.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REPLY: Accounting Service — card expired or authorization failed.
 * ---------------------------------------------------------------------------
 * SAGA ROLE - Pivot Failure:
 * When the orchestrator receives this (or a generic failure), it triggers
 * compensation: cancel kitchen ticket, reject order. This is the "pivot"
 * failure path that causes rollback of all previous compensatable steps.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardExpiredReply {

    private Long orderId;
    private String reason;
}
