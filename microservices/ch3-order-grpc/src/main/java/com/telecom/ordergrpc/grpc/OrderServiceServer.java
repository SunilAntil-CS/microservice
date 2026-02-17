package com.telecom.ordergrpc.grpc;

import com.telecom.ordergrpc.model.Order;
import com.telecom.ordergrpc.service.OrderService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MODULE 5: gRPC server implementation. Exposes the OrderService RPC on the gRPC port
 * (default 9090). Spring Boot does not support gRPC natively; we use net.devh
 * grpc-server-spring-boot-starter which scans for @GrpcService and registers the stub.
 * ------------------------------------------------------------------------------------
 * @GrpcService: Registers this class as a gRPC service; the library starts a gRPC
 * server (e.g. on grpc.server.port=9090) and attaches this implementation to the
 * OrderService definition from the .proto file. Equivalent to "implementing" the
 * service contract.
 * ------------------------------------------------------------------------------------
 * OrderServiceGrpc.OrderServiceImplBase: Generated base class from the proto. We
 * override createOrder(...). The generated code handles deserialisation of the
 * incoming request and provides the StreamObserver to send the reply.
 * ------------------------------------------------------------------------------------
 * StreamObserver<CreateOrderReply>: Async response callback. We call onNext(reply)
 * to send the reply and onCompleted() to finish. Do not call onError() unless
 * the business logic fails — then we pass the exception so the client gets a
 * gRPC error status.
 * ------------------------------------------------------------------------------------
 * Proto getters: request.getRestaurantId(), getConsumerId(), getLineItemsList()
 * (repeated fields become List in Java). Reply is built with CreateOrderReply.newBuilder().setOrderId(...).build().
 */
@GrpcService
public class OrderServiceServer extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;

    public OrderServiceServer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void createOrder(OrderServiceProto.CreateOrderRequest request,
                            StreamObserver<OrderServiceProto.CreateOrderReply> responseObserver) {
        try {
            long consumerId = request.getConsumerId();
            long restaurantId = request.getRestaurantId();
            List<Order.LineItem> lineItems = request.getLineItemsList().stream()
                    .map(li -> new Order.LineItem(li.getMenuItemId(), li.getQuantity()))
                    .collect(Collectors.toList());

            Order order = orderService.createOrder(consumerId, restaurantId, lineItems);

            OrderServiceProto.CreateOrderReply reply = OrderServiceProto.CreateOrderReply.newBuilder()
                    .setOrderId(order.getId())
                    .build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
