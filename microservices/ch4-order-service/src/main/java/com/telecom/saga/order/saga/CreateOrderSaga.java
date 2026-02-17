package com.telecom.saga.order.saga;

import com.telecom.saga.common.command.AuthorizeCardCommand;
import com.telecom.saga.common.command.CancelCreateTicketCommand;
import com.telecom.saga.common.command.CreateTicketCommand;
import com.telecom.saga.common.command.RejectOrderCommand;
import com.telecom.saga.common.reply.CreateTicketReply;
import com.telecom.saga.order.domain.Order;
import com.telecom.saga.order.domain.OrderRepository;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import io.eventuate.tram.sagas.simpledsl.SimpleSagaDsl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SAGA ORCHESTRATOR - Create Order flow using Eventuate Tram Simple DSL.
 * ---------------------------------------------------------------------------
 * SAGA ROLE: Central orchestrator that sends commands to participants (Kitchen,
 * Accounting) and runs compensating transactions when a step fails.
 *
 * Steps:
 *   1. Compensation-only: if saga fails at any point, send RejectOrderCommand to self.
 *   2. Create Ticket (Kitchen) — compensatable; compensation: CancelCreateTicketCommand.
 *   3. Authorize Card (Accounting) — PIVOT; if this fails we compensate step 2.
 *   4. Approve Order (local) — update order to APPROVED.
 * ---------------------------------------------------------------------------
 * Eventuate Tram persists saga state in saga_instance; commands/replies go via Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateOrderSaga implements SimpleSaga<CreateOrderSagaData> {

    private static final String ORDER_SERVICE_CHANNEL = "orderService";
    private static final String KITCHEN_SERVICE_CHANNEL = "kitchenService";
    private static final String ACCOUNTING_SERVICE_CHANNEL = "accountingService";

    private final OrderRepository orderRepository;

    private final SagaDefinition<CreateOrderSagaData> sagaDefinition =
            step()
                    .withCompensation(this::rejectOrder)
                    .step()
                    .invokeParticipant(this::createTicket)
                    .onReply(CreateTicketReply.class, (data, reply) -> data.setTicketId(reply.getTicketId()))
                    .withCompensation(this::cancelTicket)
                    .step()
                    .invokeParticipant(this::authorizeCard)
                    .step()
                    .invokeLocal(this::approveOrder)
                    .build();

    @Override
    public SagaDefinition<CreateOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    /** Compensation: send RejectOrder to self so order is marked REJECTED. */
    private CommandWithDestination rejectOrder(CreateOrderSagaData data) {
        return new CommandWithDestination(
                ORDER_SERVICE_CHANNEL,
                "RejectOrder",
                RejectOrderCommand.builder().orderId(data.getOrderId()).build()
        );
    }

    /** Step 1 (forward): ask Kitchen to create a ticket. */
    private CommandWithDestination createTicket(CreateOrderSagaData data) {
        CreateTicketCommand cmd = CreateTicketCommand.builder()
                .orderId(data.getOrderId())
                .restaurantId(data.getRestaurantId())
                .lineItems(data.getLineItems())
                .build();
        return new CommandWithDestination(KITCHEN_SERVICE_CHANNEL, "CreateTicket", cmd);
    }

    /** Step 1 (compensation): ask Kitchen to cancel the ticket. */
    private CommandWithDestination cancelTicket(CreateOrderSagaData data) {
        CancelCreateTicketCommand cmd = CancelCreateTicketCommand.builder()
                .ticketId(data.getTicketId())
                .orderId(data.getOrderId())
                .build();
        return new CommandWithDestination(KITCHEN_SERVICE_CHANNEL, "CancelCreateTicket", cmd);
    }

    /** Step 2 (forward): ask Accounting to authorize card — PIVOT step. */
    private CommandWithDestination authorizeCard(CreateOrderSagaData data) {
        AuthorizeCardCommand cmd = AuthorizeCardCommand.builder()
                .orderId(data.getOrderId())
                .consumerId(data.getConsumerId())
                .total(data.getOrderTotal() != null ? data.getOrderTotal() : java.math.BigDecimal.ZERO)
                .paymentToken("token-" + data.getOrderId())
                .build();
        return new CommandWithDestination(ACCOUNTING_SERVICE_CHANNEL, "AuthorizeCard", cmd);
    }

    /** Step 3 (local): approve order in local DB. */
    private void approveOrder(CreateOrderSagaData data) {
        Order order = orderRepository.findById(data.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + data.getOrderId()));
        order.approve();
        orderRepository.save(order);
        log.info("Saga completed: orderId={} APPROVED", data.getOrderId());
    }
}
