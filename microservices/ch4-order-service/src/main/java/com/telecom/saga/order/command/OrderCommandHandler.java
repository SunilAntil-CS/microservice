package com.telecom.saga.order.command;

import com.telecom.saga.common.command.RejectOrderCommand;
import com.telecom.saga.order.domain.Order;
import com.telecom.saga.order.domain.OrderRepository;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * ORDER SERVICE COMMAND HANDLER - Handles commands sent to the "orderService" channel.
 * ---------------------------------------------------------------------------
 * SAGA ROLE: When the saga fails, the orchestrator sends RejectOrderCommand to
 * this channel. This handler performs the local compensation: mark order REJECTED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommandHandler {

    private static final String ORDER_SERVICE_CHANNEL = "orderService";

    private final OrderRepository orderRepository;

    public CommandHandlers commandHandlers() {
        return CommandHandlers.from(ORDER_SERVICE_CHANNEL)
                .onMessage(RejectOrderCommand.class, this::rejectOrder)
                .build();
    }

    private List<Message> rejectOrder(CommandMessage<RejectOrderCommand> cm) {
        RejectOrderCommand cmd = cm.getCommand();
        Order order = orderRepository.findById(cmd.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + cmd.getOrderId()));
        order.reject();
        orderRepository.save(order);
        log.info("Order orderId={} rejected (saga compensation)", cmd.getOrderId());
        return Collections.singletonList(withSuccess(Collections.emptyMap()));
    }
}
