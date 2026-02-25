# Saga Orchestration with Eventuate Tram

This document describes the **production-grade Saga Orchestration** implementation using **Eventuate Tram Sagas** in the Food Ordering (FTGO-style) system, designed to be generic enough for Telecom domains.

---

## 1. Architecture Overview

### 1.1 Pattern: Orchestration

- **Orchestrator:** Order Service — owns the saga definition and sends commands to participants.
- **Participants:** Kitchen Service (compensatable), Accounting Service (pivot).
- **Messaging:** Apache Kafka; commands and replies are sent over channels. Eventuate Tram uses an **outbox** (DB table `message`) and optionally **CDC** or polling to publish to Kafka.

### 1.2 Saga Steps (Create Order)

| Step | Action | Role | Compensation |
|------|--------|------|--------------|
| 0 | (none) | — | Reject order (send `RejectOrderCommand` to order-service) |
| 1 | Create Ticket (Kitchen) | Compensatable | Cancel ticket (`CancelCreateTicketCommand`) |
| 2 | Authorize Card (Accounting) | **Pivot** | (none; failure triggers compensation of step 1) |
| 3 | Approve Order (local) | Retriable | — |

If **step 2** fails (e.g. card expired or negative total), the saga runs compensations in reverse: cancel ticket, then reject order.

---

## 2. Project Structure (Module Mapping)

| Logical name | Maven module | Responsibility |
|--------------|--------------|----------------|
| **saga-parent** | `ch4-parent` | Parent POM, dependency management, Spring Boot 2.7, Eventuate Tram versions |
| **saga-common** | `ch4-saga-common` | Shared commands, replies, DTOs (implements Tram `Command`) |
| **order-service** | `ch4-order-service` | Orchestrator: saga definition, `SagaManager`, order API, RejectOrder handler |
| **kitchen-service** | `ch4-kitchen-service` | Participant: CreateTicket / CancelCreateTicket handlers |
| **accounting-service** | `ch4-accounting-service` | Participant (pivot): AuthorizeCard handler |

---

## 3. Technical Stack

- **Java 17**
- **Spring Boot 2.7** (Eventuate Tram 0.19/0.30 are built for Spring Boot 2.x)
- **Eventuate Tram:**
  - `eventuate-tram-sagas-spring-orchestration-simple-dsl` (orchestrator)
  - `eventuate-tram-sagas-spring-orchestration` (participants)
  - `eventuate-tram-spring-jdbc-kafka` (messaging + outbox)
- **Kafka** (bootstrap `localhost:9092` when running locally)
- **Database:** H2 for local dev; MySQL for production (database-per-service: `order_db`, `kitchen_db`, `accounting_db`)

---

## 4. Key Classes and Their Saga Roles

### 4.1 Order Service (Orchestrator)

- **`CreateOrderSaga`** — Implements `SimpleSaga<CreateOrderSagaData>`. Defines steps with `step().invokeParticipant()`, `withCompensation()`, `invokeLocal()`, and `onReply()` to capture `ticketId`.
- **`CreateOrderSagaData`** — Saga state (orderId, restaurantId, lineItems, orderTotal, ticketId). Persisted in `saga_instance` by Tram.
- **`OrderService`** — Creates order (local TX) and starts the saga via `SagaManager<CreateOrderSagaData>.create(sagaData, Order.class, order.getId())`.
- **`OrderCommandHandler`** — Handles `RejectOrderCommand` on channel `orderService` (compensation: mark order REJECTED).

### 4.2 Kitchen Service (Participant – Compensatable)

- **`KitchenSagaCommandHandler`** — Builds `CommandHandlers` for channel `kitchenService`: `CreateTicketCommand` → `CreateTicketReply`; `CancelCreateTicketCommand` → success. Optional: `saga.kitchen.simulate-failure=true` to randomly fail and test rollback.
- **`KitchenCommandService`** — Local transactions: `createTicket()`, `cancelTicket()`.

### 4.3 Accounting Service (Participant – Pivot)

- **`AccountingSagaCommandHandler`** — Handles `AuthorizeCardCommand` on channel `accountingService`. Returns `CardAuthorizedReply` on success; throws on failure (e.g. when `total < 0` if `saga.accounting.fail-on-negative-total=true`) to trigger saga compensation.

---

## 5. Commands and Replies (saga-common)

| Type | Class | Channel / usage |
|------|--------|------------------|
| Command | `CreateTicketCommand` | kitchenService |
| Reply | `CreateTicketReply` | (ticketId for compensation) |
| Command | `CancelCreateTicketCommand` | kitchenService (compensation) |
| Command | `AuthorizeCardCommand` | accountingService |
| Reply | `CardAuthorizedReply` | success path |
| Reply | `CardExpiredReply` | (documented; pivot failure can also be signaled by exception) |
| Command | `RejectOrderCommand` | orderService (compensation) |

All commands implement `io.eventuate.tram.commands.common.Command`.

---

## 6. Database and Messaging

### 6.1 Eventuate Tram Tables (per service)

- **`message`** — Outbox: messages to be published to Kafka.
- **`received_messages`** — Idempotency (consumer_id, message_id).
- **`saga_instance`** — Saga state (saga_type, saga_id, state_name, saga_data_json, etc.).
- **`saga_instance_participants`** — Participant step state.

See `ch4-order-service/src/main/resources/sql/schema.sql` for reference DDL (H2/MySQL-friendly).

### 6.2 Kafka

- Bootstrap: `spring.kafka.bootstrap-servers=localhost:9092`.
- Tram uses channels (e.g. `orderService`, `kitchenService`, `accountingService`) for command/reply. Ensure Zookeeper and Kafka are up (e.g. via `docker-compose-saga.yml`).

---

## 7. Running the System

1. **Start infrastructure (optional):**
   ```bash
   docker-compose -f docker-compose-saga.yml up -d
   ```
   This starts Zookeeper, Kafka, MySQL, and (if configured) CDC. For local dev you can use H2 and only Kafka.

2. **Build:**
   ```bash
   mvn -f ch4-parent/pom.xml clean install
   ```

3. **Run services** (in order: participants first, then orchestrator):
   - Kitchen: `mvn -f ch4-kitchen-service/pom.xml spring-boot:run`
   - Accounting: `mvn -f ch4-accounting-service/pom.xml spring-boot:run`
   - Order: `mvn -f ch4-order-service/pom.xml spring-boot:run`

4. **Create an order:**
   ```bash
   curl -X POST http://localhost:8081/api/orders \
     -H "Content-Type: application/json" \
     -d '{"restaurantId":1,"consumerId":1,"lineItems":[{"menuItemId":"item1","quantity":2}],"orderTotal":25.00}'
   ```
   Poll `GET http://localhost:8081/api/orders/{id}` until state is `APPROVED` or `REJECTED`.

5. **Test compensation (pivot failure):** Send `orderTotal: -1`; Accounting should throw, saga should compensate (cancel ticket, reject order).

6. **Test Kitchen failure:** Set `saga.kitchen.simulate-failure=true` and place an order; saga should compensate after create ticket fails or a later step fails.

---

## 8. Configuration Summary

- **Order Service:** `server.port=8081`, Kafka bootstrap, DB (H2 or MySQL).
- **Kitchen Service:** `server.port=8082`, `saga.kitchen.simulate-failure=false`.
- **Accounting Service:** `server.port=8083`, `saga.accounting.fail-on-negative-total=true` (throw on negative total to trigger compensation).

---

## 9. Applying to Telecom Domains

The same pattern applies:

- **Orchestrator:** e.g. “Provisioning Service” that runs a multi-step flow.
- **Participants:** e.g. “Network Resource Service” (reserve/release), “Billing Service” (reserve credit / release). Reserve = compensatable; a “commit” step can be pivot.
- **Commands/Replies:** Define in a shared module (e.g. `telecom-saga-common`) and implement `Command` where needed.

Replace “Create Ticket” / “Authorize Card” with “Reserve Resource” / “Reserve Credit” and keep the same saga structure and compensation logic.
