# Saga Implementation – Code Reference and Explanations

This document walks through the **code-level** design and comments for the Eventuate Tram Saga implementation.

---

## 1. Why Spring Boot 2.7 (not 3.x)?

Eventuate Tram artifacts `0.19.0.RELEASE` (Sagas) and `0.30.0.RELEASE` (Core) target **Spring Boot 2.x** and **javax** namespaces (`javax.persistence`, `javax.validation`). The saga modules use **Spring Boot 2.7.18** and **Java 17** so that Tram works without modification. Entities and validation use `javax.*` in order-service and kitchen-service.

---

## 2. saga-common: Commands and Replies

- Every **command** sent by the orchestrator implements `io.eventuate.tram.commands.common.Command` so it can be wrapped in `CommandWithDestination` and sent to a channel.
- **Replies** are plain DTOs; the framework serializes them into the reply message. The orchestrator uses `onReply(CreateTicketReply.class, (data, reply) -> data.setTicketId(reply.getTicketId()))` to update saga data from the Kitchen reply.
- **RejectOrderCommand** is the compensation for “order created”: when the saga fails, the orchestrator sends this to the `orderService` channel so the order is marked REJECTED.

---

## 3. CreateOrderSaga (Orchestrator) – Step-by-Step

```text
step()
  .withCompensation(this::rejectOrder)   // Step 0: no forward action; if saga fails, reject order
  .step()
  .invokeParticipant(this::createTicket) // Step 1: send CreateTicketCommand to kitchenService
  .onReply(CreateTicketReply.class, (data, reply) -> data.setTicketId(reply.getTicketId()))
  .withCompensation(this::cancelTicket)   // Step 1 compensation: send CancelCreateTicketCommand
  .step()
  .invokeParticipant(this::authorizeCard) // Step 2: send AuthorizeCardCommand to accountingService (PIVOT)
  .step()
  .invokeLocal(this::approveOrder)        // Step 3: local DB update to APPROVED
  .build();
```

- **rejectOrder(data)** returns `CommandWithDestination("orderService", "RejectOrder", new RejectOrderCommand(data.getOrderId()))`.
- **createTicket(data)** returns `CommandWithDestination("kitchenService", "CreateTicket", CreateTicketCommand(...))`.
- **cancelTicket(data)** uses `data.getTicketId()` (set from `onReply`) and returns `CommandWithDestination("kitchenService", "CancelCreateTicket", CancelCreateTicketCommand(...))`.
- **authorizeCard(data)** returns `CommandWithDestination("accountingService", "AuthorizeCard", AuthorizeCardCommand(...))`.
- **approveOrder(data)** runs in-process: load order, `order.approve()`, save.

If **any** participant step fails (exception or failure reply, depending on Tram behavior), the framework runs compensations in reverse order (cancel ticket, then reject order).

---

## 4. OrderService – Starting the Saga

- **OrderService.createOrder(...)** creates the order entity (PENDING), saves it, builds `CreateOrderSagaData`, and calls **SagaManager&lt;CreateOrderSagaData&gt;.create(sagaData, Order.class, order.getId())**.
- The **SagaManager** is provided by Eventuate Tram’s Spring orchestration; it persists the saga in `saga_instance` and sends the first step’s command(s) asynchronously. The REST endpoint returns immediately; the client can poll `GET /api/orders/{id}` to see when the order becomes APPROVED or REJECTED.

---

## 5. Participant Command Handlers

- **OrderCommandHandler** — Listens on `orderService`. Handles `RejectOrderCommand`: load order, `order.reject()`, save. Returns `withSuccess(Collections.emptyMap())` (or similar) so the framework treats the compensation as successful.
- **KitchenSagaCommandHandler** — Listens on `kitchenService`. Handles `CreateTicketCommand` (calls `KitchenCommandService.createTicket`, returns `CreateTicketReply`) and `CancelCreateTicketCommand` (calls `cancelTicket`, returns success). Optional random failure when `saga.kitchen.simulate-failure=true` for testing rollback.
- **AccountingSagaCommandHandler** — Listens on `accountingService`. Handles `AuthorizeCardCommand`: if `saga.accounting.fail-on-negative-total=true` and total &lt; 0, **throws** so the saga triggers compensation; otherwise returns `CardAuthorizedReply`.

All handlers are registered via `CommandHandlers.from(channel).onMessage(CommandClass.class, this::handler).build()` and exposed as beans (e.g. in `*TramConfig`).

---

## 6. Schema (Eventuate Tram)

- **message** — Outbox: producer writes here; CDC or polling publishes to Kafka.
- **received_messages** — Idempotency key (consumer_id, message_id).
- **saga_instance** — One row per saga (id, saga_type, saga_id, state_name, saga_data_json, etc.).
- **saga_instance_participants** — Tracks participant steps.

Order service’s `schema.sql` provides reference DDL; Tram may also create/update these when using its JDBC modules.

---

## 7. Docker and CDC

- **docker-compose-saga.yml** starts Zookeeper, Kafka, and MySQL (with init script creating `order_db`, `kitchen_db`, `accounting_db`). The CDC service is optional; if you use it, ensure the image and configuration match your Eventuate Tram/CDC version. For local development, H2 + Kafka is often enough.

---

## 8. Summary Table: “Who does what”

| Component | Responsibility |
|-----------|----------------|
| **CreateOrderSaga** | Defines steps and compensations; sends commands via `CommandWithDestination`. |
| **SagaManager** | Creates saga instance, persists state, drives step execution and compensation. |
| **OrderCommandHandler** | Executes RejectOrderCommand (mark order REJECTED). |
| **KitchenSagaCommandHandler** | CreateTicket (reply with ticketId), CancelCreateTicket (compensation). |
| **AccountingSagaCommandHandler** | AuthorizeCard (success reply or throw to trigger compensation). |

This structure keeps the saga logic in one place (CreateOrderSaga), participants stateless (handle one command at a time), and compensations explicit and reversible.
