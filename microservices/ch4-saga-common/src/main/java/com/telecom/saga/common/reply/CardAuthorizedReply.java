package com.telecom.saga.common.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REPLY: Accounting Service — card authorization succeeded.
 * ---------------------------------------------------------------------------
 * The orchestrator uses this to proceed to the next step (Approve Order).
 * No compensation is needed for "authorize" in the classic pivot sense;
 * business may still define a "release authorization" compensating command.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAuthorizedReply {

    private Long orderId;
    private String authorizationId;
}
