package com.telecom.ordergrpc.repository;

import com.telecom.ordergrpc.model.Order;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory order store. For Module 5 the focus is gRPC contract and server;
 * in production you would use JPA or another persistence layer.
 */
@Repository
public class OrderRepository {

    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Order save(Order order) {
        if (order.getId() == 0) {
            order.setId(idGenerator.getAndIncrement());
        }
        store.put(order.getId(), order);
        return order;
    }

    public Order findById(long id) {
        return store.get(id);
    }
}
