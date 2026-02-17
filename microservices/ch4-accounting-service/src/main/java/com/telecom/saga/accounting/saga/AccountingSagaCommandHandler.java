package com.telecom.saga.accounting.saga;

import com.telecom.saga.common.command.AuthorizeCardCommand;
import com.telecom.saga.common.reply.CardAuthorizedReply;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * SAGA PARTICIPANT - Pivot Transaction: Authorize Card.
 * ---------------------------------------------------------------------------
 * SAGA ROLE: Handles AuthorizeCardCommand. Success -> CardAuthorizedReply (saga
 * continues to Approve Order). Failure -> CardExpiredReply or exception (saga
 * runs compensation: cancel ticket, reject order).
 * ---------------------------------------------------------------------------
 * Simulates pivot failure when order total &lt; 0 (configurable) for testing.
 */
@Slf4j
@Component
public class AccountingSagaCommandHandler {

    private static final String ACCOUNTING_SERVICE_CHANNEL = "accountingService";

    /** When true, reject authorization if total < 0 (triggers saga compensation). */
    @Value("${saga.accounting.fail-on-negative-total:true}")
    private boolean failOnNegativeTotal;

    public CommandHandlers commandHandlers() {
        return CommandHandlers.from(ACCOUNTING_SERVICE_CHANNEL)
                .onMessage(AuthorizeCardCommand.class, this::handleAuthorizeCard)
                .build();
    }

    private List<Message> handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> cm) {
        AuthorizeCardCommand cmd = cm.getCommand();
        if (failOnNegativeTotal && cmd.getTotal() != null && cmd.getTotal().signum() < 0) {
            log.warn("Pivot failure: orderId={} has negative total {} (triggering saga compensation)", cmd.getOrderId(), cmd.getTotal());
            throw new RuntimeException("Card authorization failed: negative total not allowed (orderId=" + cmd.getOrderId() + ")");
        }
        String authId = "auth-" + UUID.randomUUID();
        log.info("Authorized orderId={} authId={}", cmd.getOrderId(), authId);
        return Collections.singletonList(withSuccess(
                CardAuthorizedReply.builder()
                        .orderId(cmd.getOrderId())
                        .authorizationId(authId)
                        .build()));
    }
}
