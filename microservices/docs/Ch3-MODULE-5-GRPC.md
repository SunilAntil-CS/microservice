# Chapter 3 – Module 5: gRPC (High-Performance Inter-Process Communication)

**This repo:** Chapter 3 (Microservices). Notes and interview cheat sheet for **Module 5** — when and how to use gRPC instead of REST for internal service-to-service calls.

---

## Why gRPC?

**REST (JSON):**
- Pros: Human-readable, easy to debug (curl, Postman).
- Cons: Heavier — more bytes ("status" = 6 chars), JSON parsing is CPU-heavy at scale.

**gRPC (Protocol Buffers):**
- Pros: Binary, compact (e.g. 1 byte for a status code), strong typing (schema), HTTP/2 multiplexing.
- Cons: Not human-readable; need tools like `grpcurl` to test.

**Telecom scenario:** Between AMF (Access Management) and SMF (Session Management) you can have 10k+ req/s. JSON overhead would burn CPU; binary protocols (gRPC, Diameter) save resources.

---

## Contract-First

We do **not** write Java classes first. We write a **contract** (`.proto` — IDL).

1. **.proto file:** Defines the service (RPCs) and messages (request/reply types). Field numbers are part of the wire format (binary efficiency).
2. **Code generation:** Maven (protobuf-maven-plugin) runs `protoc` and generates Java (and gRPC stubs). The generated code lives in `target/generated-sources/protobuf`.
3. **Server:** We implement the generated base class (e.g. `OrderServiceGrpc.OrderServiceImplBase`), map proto ↔ domain, call business logic, and send the reply via `StreamObserver.onNext()` and `onCompleted()`.

---

## Implementation Summary

| Step | What |
|------|------|
| 1 | Define `OrderService.proto`: `service OrderService { rpc createOrder (...) returns (...); }` and message types. |
| 2 | Maven `generate-sources`: generates `OrderServiceProto`, `OrderServiceGrpc` (with `OrderServiceImplBase`). |
| 3 | Server class: `@GrpcService`, extend `OrderServiceGrpc.OrderServiceImplBase`, override `createOrder(...)`. Map request → domain → call `OrderService.createOrder(...)` → build reply → `responseObserver.onNext(reply); onCompleted();`. |
| 4 | gRPC server runs on a separate port (e.g. 9090) via net.devh grpc-server-spring-boot-starter. |

---

## Interview: REST vs gRPC

**Q: When would you choose gRPC over REST?**

**Senior answer:**  
"I use **REST** for external/public APIs (mobile, web) because of browser support and ease of integration. I use **gRPC** for **internal** service-to-service communication when:

1. **Latency is critical** (e.g. real-time trading, telecom signalling).
2. **Bandwidth is constrained** (e.g. IoT).
3. **Strict typing across languages** is needed (e.g. Go service talking to Java service; one .proto, multiple language stubs)."

---

## Project Layout (Chapter 3 – Module 5)

```
ch3-order-grpc/
├── src/main/proto/OrderService.proto   # Contract (IDL)
├── src/main/java/.../OrderGrpcApplication.java
├── src/main/java/.../grpc/OrderServiceServer.java   # @GrpcService, extends generated base
├── src/main/java/.../service/OrderService.java      # Business logic
├── src/main/java/.../model/Order.java
├── src/main/java/.../repository/OrderRepository.java
└── src/main/resources/application.properties       # grpc.server.port=9090
```

Generated (by Maven): `target/generated-sources/protobuf` → `OrderServiceProto`, `OrderServiceGrpc`.

---

## How to Run

1. Generate stubs and compile: `cd ch3-order-grpc && mvn compile` (or `mvn generate-sources` then build).
2. Run: `mvn spring-boot:run`. App listens on 8084 (HTTP/actuator); gRPC on **9090**.
3. Test with **grpcurl** (install separately):
   - List services: `grpcurl -plaintext localhost:9090 list`
   - Call: `grpcurl -plaintext -d '{"restaurant_id":1,"consumer_id":2,"line_items":[{"menu_item_id":"item-1","quantity":2}]}' localhost:9090 com.telecom.ordergrpc.grpc.OrderService/createOrder`

---

## Key Terms

- **IDL** — Interface Definition Language (.proto).
- **Protocol Buffers** — Binary serialisation; schema-driven, compact.
- **StreamObserver** — Async response callback in gRPC Java: `onNext(reply)`, `onCompleted()`, `onError(t)`.
- **@GrpcService** — net.devh: register this class as a gRPC service implementation.
