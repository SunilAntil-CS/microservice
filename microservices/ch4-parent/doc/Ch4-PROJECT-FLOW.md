# Chapter 4 Module 1: Project Flow — Saga Orchestration

This document explains **how the Chapter 4 Module 1 project works** end-to-end: from theory (death of ACID, birth of Sagas) to the actual code flow and how to run it.

---

## 1. Why This Project Exists: The Death of ACID

In a **monolith**, one database and one `@Transactional` method can do:

- Create order
- Reserve customer credit
- Create kitchen ticket

**All or nothing** (ACID).

In **microservices**, each service has its **own database** (Database-per-Service). You **cannot** span a single transaction across Order DB, Customer DB, and Kitchen DB. So we use a **Saga**: a sequence of **local transactions** with explicit **compensation** (undo) when something fails.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ORDER SERVICE (Orchestrator)                       │
│  Port: 8081  │  DB: H2 (orders, order_line_items)                        │
│                                                                          │
│  • OrderController: POST /api/orders  →  create order, start saga        │
│  • CreateOrderSaga:  execute(sagaData) →  step 1 → step 2 → step 3       │
│  • KitchenServiceProxy: HTTP calls to Kitchen (create / cancel)          │
└─────────────────────────────────────────────────────────────────────────┘
                    │                                    │
                    │  POST /api/tickets/create          │  POST /api/tickets/cancel
                    │  (CreateTicketCommand)             │  (CancelCreateTicketCommand)
                    ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        KITCHEN SERVICE (Participant)                      │
│  Port: 8082  │  DB: H2 (tickets, ticket_line_items)                      │
│                                                                          │
│  • TicketCommandController: /api/tickets/create, /api/tickets/cancel    │
│  • KitchenCommandService: createTicket(), cancelTicket() — local TX only   │
└─────────────────────────────────────────────────────────────────────────┘
```

**Shared contract** (no service owns the other’s DB):

- **ch4-saga-common**: `CreateTicketCommand`, `CancelCreateTicketCommand`, `CreateTicketReply`, `TicketLineItem`

---

## 3. End-to-End Flow (Happy Path)

1. **Client** sends `POST /api/orders` to Order Service with `restaurantId`, `consumerId`, `lineItems`.
2. **Order Service**
   - **Local TX 1:** Creates `Order` in state `PENDING` and saves to Order DB.
   - Builds `CreateOrderSagaData` and calls `CreateOrderSaga.execute(sagaData)`.
3. **Saga – Step 1 (Kitchen – Compensatable)**
   - Orchestrator sends `CreateTicketCommand` to Kitchen (HTTP `POST /api/tickets/create`).
   - Kitchen runs **local TX:** creates `Ticket` (state `CREATED`), saves to Kitchen DB, returns `CreateTicketReply(ticketId)`.
   - Orchestrator stores `ticketId` in saga data (needed for compensation).
4. **Saga – Step 2 (Optional)**  
   In a full system: call Accounting (authorize). If it fails → go to compensation. (Skipped in this minimal project.)
5. **Saga – Step 3 (Approve – Retriable)**
   - Orchestrator loads `Order` from DB, sets state to `APPROVED`, saves.
6. **Response**
   - Order Service returns the order with state `APPROVED` (and created order id).

**Result:** Order is in Order DB as APPROVED; Ticket is in Kitchen DB as CREATED. Both are consistent.

---

## 4. End-to-End Flow (Failure Path — Compensation)

Suppose **Step 1 (Kitchen)** succeeds but **Step 2 or 3** would fail (in our minimal flow we only have Step 1 and Step 3; you can imagine Step 2 as “accounting authorize” that fails):

1. Order is created (PENDING), Ticket is created (CREATED).
2. A later step fails (e.g. “authorize” fails or we simulate a failure).
3. **Saga** runs **compensation** in **reverse order**:
   - **Compensate Step 1:** Send `CancelCreateTicketCommand` to Kitchen with the stored `ticketId`. Kitchen runs **local TX:** sets ticket to `CANCELLED`.
   - **Compensate “Step 0”:** Order Service sets Order state to `REJECTED` and saves.
4. Client sees order in `REJECTED` state; Kitchen has no “active” ticket for that order.

**Rule:** Only steps that **committed** get a compensating call. The orchestrator tracks what was done (e.g. `ticketId`) and calls only the needed compensations.

---

## 5. Module Roles

| Module               | Role           | Responsibility                                                                 |
|----------------------|----------------|---------------------------------------------------------------------------------|
| **ch4-saga-common**  | Contract       | Commands, replies, DTOs shared by Order and Kitchen. No business logic.       |
| **ch4-order-service**| Orchestrator   | Owns Order aggregate; runs saga steps and compensation; calls Kitchen via proxy.|
| **ch4-kitchen-service** | Participant | Owns Ticket aggregate; executes create/cancel in local transactions only.      |

---

## 6. Key Concepts (From the Notes)

- **ACID is gone** across services: we only have local transactions.
- **Saga** = sequence of local transactions + **compensation** (undo) on failure.
- **Compensatable step:** has a defined “undo” (e.g. Create Ticket → Cancel Ticket).
- **Orchestration:** one central component (Order Service) drives the flow and calls participants (Kitchen).
- **Database-per-Service:** Order DB and Kitchen DB are separate; no shared transaction.
- **Eventual consistency:** For a short time, order can be PENDING while the saga runs; then it becomes APPROVED or REJECTED.

---

## 7. How to Run the Project

**Prerequisites:** Java 17+, Maven.

1. **Build and install the common module** (so Order and Kitchen can depend on it):
   ```bash
   cd microservices/ch4-saga-common
   mvn clean install
   ```
2. **Start Kitchen Service first** (participant must be up when Order calls it):
   ```bash
   cd microservices/ch4-kitchen-service
   mvn spring-boot:run
   ```
   Kitchen will listen on **8082**.
3. **Start Order Service** (orchestrator):
   ```bash
   cd microservices/ch4-order-service
   mvn spring-boot:run
   ```
   Order will listen on **8081**.
4. **Create an order** (happy path):
   ```bash
   curl -X POST http://localhost:8081/api/orders \
     -H "Content-Type: application/json" \
     -d '{"restaurantId":1,"consumerId":100,"lineItems":[{"menuItemId":"item-1","quantity":2}]}'
   ```
   Expected: response with `state: "APPROVED"`.
5. **Optional – test compensation:**  
   Stop Kitchen Service, create another order. Order Service will fail when calling Kitchen and run compensation: the new order will be `REJECTED`, and any previously created ticket for that order would have been cancelled if the saga had progressed that far.

---

## 8. File Map (Where to Look)

- **Flow and state machine:** `ch4-order-service` → `CreateOrderSaga.execute()` and `compensate()`.
- **First local TX (order):** `OrderController.createOrder()` → `Order.createOrder()` → `orderRepository.save()`.
- **Outbound call to Kitchen:** `KitchenServiceProxy.createTicket()` / `cancelTicket()`.
- **Participant logic:** `ch4-kitchen-service` → `KitchenCommandService.createTicket()` / `cancelTicket()`.
- **Shared types:** `ch4-saga-common` → `command` and `reply` packages.

---

## 9. Production Notes

- **Durability:** This sample uses in-process, synchronous saga execution. For production, use a saga framework (e.g. **Eventuate Tram**) that persists saga state and uses messaging (Kafka), so the orchestrator can recover after a crash and continue or compensate.
- **Resilience:** Add **Circuit Breaker** and **timeouts** on `KitchenServiceProxy` so a down or slow Kitchen does not hang the orchestrator.
- **Idempotency:** Participants (e.g. Kitchen’s cancel) should be idempotent; our cancel is safe to call multiple times for the same ticket.
- **Observability:** Add tracing (e.g. correlation ID from order to kitchen calls) and structured logging for saga steps and compensations.

This flow document and the code comments in the project are aligned so you can follow both to understand Chapter 4 Module 1 end-to-end.
