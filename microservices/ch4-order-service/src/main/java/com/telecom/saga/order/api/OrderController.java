package com.telecom.saga.order.api;

import com.telecom.saga.common.dto.TicketLineItem;
import com.telecom.saga.order.domain.Order;
import com.telecom.saga.order.domain.OrderRepository;
import com.telecom.saga.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * REST API for placing orders. Each order starts the Create Order Saga (Eventuate Tram).
 * Saga runs asynchronously; order state becomes APPROVED or REJECTED when the saga completes.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Place a new order. Persists order (PENDING) and starts the saga asynchronously.
     * Poll GET /api/orders/{id} to see final state (APPROVED or REJECTED).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.getRestaurantId(),
                request.getConsumerId(),
                request.getLineItems(),
                request.getOrderTotal()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** DTO for creating an order; uses shared TicketLineItem for line items. */
    @lombok.Data
    public static class CreateOrderRequest {
        @NotNull
        private Long restaurantId;
        @NotNull
        private Long consumerId;
        private List<TicketLineItem> lineItems;
        /** Order total for card authorization (Pivot step). */
        private BigDecimal orderTotal;
    }

    /** DTO for order response. */
    @lombok.Data
    public static class OrderResponse {
        private Long id;
        private Long restaurantId;
        private Long consumerId;
        private String state;
        private String createdAt;

        public static OrderResponse from(Order order) {
            OrderResponse r = new OrderResponse();
            r.setId(order.getId());
            r.setRestaurantId(order.getRestaurantId());
            r.setConsumerId(order.getConsumerId());
            r.setState(order.getState().name());
            r.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
            return r;
        }
    }
}
