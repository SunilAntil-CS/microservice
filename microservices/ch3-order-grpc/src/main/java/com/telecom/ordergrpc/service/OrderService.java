package com.telecom.ordergrpc.service;

import com.telecom.ordergrpc.model.Order;
import com.telecom.ordergrpc.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic: create order from domain parameters. Called by the gRPC server
 * after mapping proto request to domain types. Keeps gRPC layer thin.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(long consumerId, long restaurantId, List<Order.LineItem> lineItems) {
        Order order = new Order(0, restaurantId, consumerId, lineItems);
        return orderRepository.save(order);
    }
}
