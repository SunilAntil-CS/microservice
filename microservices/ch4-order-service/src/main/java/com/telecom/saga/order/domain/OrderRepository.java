package com.telecom.saga.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for Order.
 * CONCEPT: All persistence for the Order aggregate stays in this service's DB.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
