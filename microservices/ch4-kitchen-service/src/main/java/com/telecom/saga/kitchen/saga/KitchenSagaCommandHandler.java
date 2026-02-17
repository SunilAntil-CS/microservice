package com.telecom.saga.kitchen.saga;

import com.telecom.saga.common.command.CancelCreateTicketCommand;
import com.telecom.saga.common.command.CreateTicketCommand;
import com.telecom.saga.common.reply.CreateTicketReply;
import com.telecom.saga.kitchen.service.KitchenCommandService;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * SAGA PARTICIPANT - Handles commands from the Order Service (orchestrator) via Eventuate Tram.
 * ---------------------------------------------------------------------------
 * SAGA ROLE: Compensatable transaction participant. Handles CreateTicketCommand
 * (forward) and CancelCreateTicketCommand (compensation). Replies are sent back
 * to the saga so it can continue or trigger compensation.
 * ---------------------------------------------------------------------------
 * Channel "kitchenService" must match the destination used by CreateOrderSaga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenSagaCommandHandler {

    private static final String KITCHEN_SERVICE_CHANNEL = "kitchenService";

    private final KitchenCommandService kitchenCommandService;

    /** Set to true to simulate random failures (for testing rollback). */
    @Value("${saga.kitchen.simulate-failure:false}")
    private boolean simulateFailure;

    private final Random random = new Random();

    public CommandHandlers commandHandlers() {
        return CommandHandlers.from(KITCHEN_SERVICE_CHANNEL)
                .onMessage(CreateTicketCommand.class, this::handleCreateTicket)
                .onMessage(CancelCreateTicketCommand.class, this::handleCancelTicket)
                .build();
    }

    private List<Message> handleCreateTicket(CommandMessage<CreateTicketCommand> cm) {
        CreateTicketCommand cmd = cm.getCommand();
        if (simulateFailure && random.nextBoolean()) {
            log.warn("Simulated failure for orderId={} (rollback test)", cmd.getOrderId());
            throw new RuntimeException("Simulated kitchen failure for testing saga compensation");
        }
        CreateTicketReply reply = kitchenCommandService.createTicket(cmd);
        return Collections.singletonList(withSuccess(reply));
    }

    private List<Message> handleCancelTicket(CommandMessage<CancelCreateTicketCommand> cm) {
        kitchenCommandService.cancelTicket(cm.getCommand());
        return Collections.singletonList(withSuccess(Collections.emptyMap()));
    }
}
