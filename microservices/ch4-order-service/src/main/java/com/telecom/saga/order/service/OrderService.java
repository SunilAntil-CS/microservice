package com.telecom.saga.order.service;

import com.telecom.saga.order.domain.Order;
import com.telecom.saga.order.domain.OrderRepository;
import com.telecom.saga.order.saga.CreateOrderSagaData;
import io.eventuate.tram.sagas.orchestration.SagaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ORDER SERVICE - Starts the Create Order Saga via Eventuate Tram.
 * ---------------------------------------------------------------------------
 * SAGA ROLE: This service performs the first local transaction (create order
 * in PENDING state) and then starts the saga by calling SagaManager.create().
 * The saga runs asynchronously: commands are sent to Kafka, participants
 * reply, and the orchestrator advances or compensates. The order state
 * (APPROVED/REJECTED) is updated when the saga completes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaManager<CreateOrderSagaData> createOrderSagaManager;

    /**
     * Create order (first local TX) and start the Create Order Saga.
     * Saga runs asynchronously; order state will become APPROVED or REJECTED when
     * it completes.
     */
    @Transactional
    public Order createOrder(Long restaurantId, Long consumerId,
            java.util.List<com.telecom.saga.common.dto.TicketLineItem> lineItems,
            BigDecimal orderTotal) {
        Order order = Order.createOrder(restaurantId, consumerId, lineItems);
        order = orderRepository.save(order);

        CreateOrderSagaData sagaData = CreateOrderSagaData.builder()
                .orderId(order.getId())
                .restaurantId(restaurantId)
                .consumerId(consumerId)
                .lineItems(lineItems)
                .orderTotal(orderTotal != null ? orderTotal : BigDecimal.ZERO)
                .build();

        createOrderSagaManager.create(sagaData, Order.class, order.getId());
        log.info("Created order id={} and started CreateOrderSaga", order.getId());
        return order;
    }
}
