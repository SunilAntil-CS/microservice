# Create Order Saga – Code Flow

This document describes the **code flow** from the HTTP request through saga execution and compensation.

---

## 1. Entry: REST → Order Creation and Saga Start (Synchronous)

```
Client
  │  POST /api/orders  { restaurantId, consumerId, lineItems, orderTotal }
  ▼
OrderController.createOrder(request)
  │
  ├─► OrderService.createOrder(restaurantId, consumerId, lineItems, orderTotal)
  │     │
  │     ├─► Order.createOrder(...)  →  new Order(PENDING)
  │     ├─► orderRepository.save(order)   // ① First local TX: order in DB
  │     ├─► CreateOrderSagaData.builder()
  │     │      .orderId(order.getId())
  │     │      .restaurantId, .consumerId, .lineItems, .orderTotal
  │     │      .build()
  │     │
  │     └─► createOrderSagaManager.create(sagaData, Order.class, order.getId())
  │           // ② SagaManager persists saga in saga_instance, then starts saga (async)
  │
  └─► return 201 + OrderResponse (order still PENDING)
```

**Files:** `OrderController` → `OrderService` → `SagaManager.create()` (Eventuate Tram).  
The HTTP response returns immediately; the saga runs **asynchronously** via Kafka.

---

## 2. What Happens Right After Line 51 (`createOrderSagaManager.create(...)`)

Right after **line 51** in `OrderService.java`, control goes into **Eventuate Tram’s SagaManager** (framework code). Your `OrderService.createOrder()` then returns and the HTTP response is sent. The rest of the saga runs **asynchronously** in the background. Here is the flow from that point.

### 2.1 Inside `SagaManager.create(sagaData, Order.class, order.getId())` (framework)

Conceptually the framework:

1. **Creates a saga instance**
   - Generates a saga instance ID.
   - Saves a row in **`saga_instance`** with:
     - `saga_type` (from your saga, e.g. tied to `CreateOrderSaga`),
     - `saga_id` = `order.getId()` (so you can correlate saga ↔ order),
     - `saga_data_json` = serialized `CreateOrderSagaData` (orderId, restaurantId, lineItems, orderTotal, etc.),
     - current step / state.
2. **Starts the saga**
   - Looks up the **saga definition** from your **`CreateOrderSaga.getSagaDefinition()`** (the `step().withCompensation(...).step().invokeParticipant(...)...` you defined).
   - Begins executing the **first real step** (Step 1: Create Ticket). It does **not** run in the same thread as your `OrderService`; it’s scheduled/run asynchronously (e.g. after your transaction commits, or via a message).

So **after line 51**, the next thing that “runs” is **inside the framework**: persist saga instance, then start executing the saga definition.

### 2.2 First step the framework runs: Step 1 – Create Ticket

The saga definition says: Step 1 = **invokeParticipant(this::createTicket)**.

So the framework:

1. Loads **CreateOrderSagaData** from the saga instance (your `sagaData`).
2. Calls **your** method: **`CreateOrderSaga.createTicket(data)`** (in `CreateOrderSaga.java`).
3. Your method returns a **`CommandWithDestination`**: channel = `"kitchenService"`, command = **CreateTicketCommand** (orderId, restaurantId, lineItems).
4. The framework **sends that command to Kafka** (to the topic/channel used for `kitchenService`), and **saves** that it is “waiting for a reply” for this step (in saga state / outbox).

So the **first place you can “see” the flow again in your code** after line 51 is:

- **`CreateOrderSaga.createTicket(CreateOrderSagaData data)`**  
  That’s the method that builds **CreateTicketCommand** and returns **CommandWithDestination** for Kitchen.

### 2.3 Who consumes the command? Kitchen service

Another process (or the same app, if Kitchen runs in the same JVM) is **subscribed to the `kitchenService` channel**. When the command message arrives from Kafka:

1. **Eventuate Tram** deserializes the message into **CreateTicketCommand**.
2. It finds the handler that registered for that command on `kitchenService`: **`KitchenSagaCommandHandler.handleCreateTicket(CommandMessage<CreateTicketCommand> cm)`**.
3. That handler runs:
   - **`KitchenCommandService.createTicket(cmd)`** → creates **Ticket**, saves it, returns **CreateTicketReply(ticketId, orderId)**.
   - Handler then returns that reply (e.g. **withSuccess(CreateTicketReply)**).
4. The framework **sends that reply back** (via Kafka) to the **order service** (saga orchestrator), including a **correlation id** so the framework knows which saga instance and which step the reply belongs to.

So the **next place you see the flow in your code** after `createTicket(data)` is:

- **`KitchenSagaCommandHandler.handleCreateTicket(...)`**  
- **`KitchenCommandService.createTicket(cmd)`**

### 2.4 Back in the Order Service: saga continues

When the **order service** (saga side) **consumes the reply** from Kafka:

1. **SagaManager** (framework) receives the reply and:
   - Finds the saga instance (by correlation id).
   - Applies **onReply**: **`(data, reply) -> data.setTicketId(reply.getTicketId())`** so **CreateOrderSagaData** now has **ticketId**.
   - Persists updated saga data.
   - Marks Step 1 complete and goes to **Step 2**.
2. For Step 2 it calls **your** **`CreateOrderSaga.authorizeCard(data)`**.
3. Your method returns **CommandWithDestination** for **accountingService** with **AuthorizeCardCommand**.
4. Framework sends that to Kafka → **Accounting Service** consumes it → **AccountingSagaCommandHandler.handleAuthorizeCard(...)** runs.
5. When the reply comes back (or an exception/failure), the framework either:
   - Continues to **Step 3**: calls **`CreateOrderSaga.approveOrder(data)`** (local: load order, approve, save), or
   - Runs **compensations** (cancel ticket, then reject order) if Step 2 failed.

So **after line 51**, the flow in **your** code is:

1. **CreateOrderSaga.createTicket(data)** → command to Kitchen  
2. **KitchenSagaCommandHandler.handleCreateTicket** → **KitchenCommandService.createTicket**  
3. (Framework handles reply and updates saga data.)  
4. **CreateOrderSaga.authorizeCard(data)** → command to Accounting  
5. **AccountingSagaCommandHandler.handleAuthorizeCard**  
6. (Framework handles reply or failure.)  
7. **CreateOrderSaga.approveOrder(data)** (success path), or compensation: **cancelTicket** → **rejectOrder** (and their handlers in Kitchen and Order).

### 2.5 One-line summary

**After line 51:** the framework saves the saga and asynchronously runs your saga definition; the **next code of yours that runs** is **`CreateOrderSaga.createTicket(sagaData)`**, then the **Kitchen** handler when the command is consumed from Kafka.

---

## 2.6 How does `CreateOrderSaga` get into the picture? (We only call `SagaManager.create(...)`)

**OrderService** never references **CreateOrderSaga** directly. The connection is done by **Spring** and **Eventuate Tram** at startup:

1. **CreateOrderSaga** is a **`@Component`** and implements **`SimpleSaga<CreateOrderSagaData>`**. So Spring creates a bean: **CreateOrderSaga**.

2. **Eventuate Tram’s Spring config** (from the saga-orchestration JAR) **discovers all beans that implement `SimpleSaga<?>`**. For each such saga it builds a **SagaManager** that:
   - Is typed with the same data as the saga: **SagaManager&lt;CreateOrderSagaData&gt;**
   - **Holds an internal reference to that saga instance** (your **CreateOrderSaga** bean)
   - When **create()** or later step execution runs, it calls **saga.getSagaDefinition()** and uses that definition to run steps (and will call **createTicket(data)**, **authorizeCard(data)**, etc. on that same saga instance)

3. That **SagaManager&lt;CreateOrderSagaData&gt;** is registered as a **Spring bean**. Naming is often derived from the saga class (e.g. **createOrderSagaManager** for **CreateOrderSaga**).

4. **OrderService** injects **SagaManager&lt;CreateOrderSagaData&gt; createOrderSagaManager**. Spring has only one bean of that type — the one the framework created for **CreateOrderSaga** — so it injects **that** manager.

5. So when you call **createOrderSagaManager.create(sagaData, Order.class, order.getId())**:
   - You are calling the **SagaManager** that was created for **CreateOrderSaga**.
   - That manager **already has a reference to your CreateOrderSaga** bean.
   - Inside **create()**, the framework uses that reference to call **getSagaDefinition()** and then executes the steps by calling **createTicket(data)**, **authorizeCard(data)**, **approveOrder(data)** (and compensations) on **CreateOrderSaga**.

So **CreateOrderSaga** comes into the picture because the **SagaManager** you are calling was built by the framework **from** the **CreateOrderSaga** bean. The link is **SagaManager ↔ CreateOrderSaga** done at startup by Eventuate Tram, not by any explicit reference in **OrderService**.

```
Startup:
  CreateOrderSaga (@Component, SimpleSaga<CreateOrderSagaData>)
       │
       │  Eventuate Tram finds it and creates
       ▼
  SagaManager<CreateOrderSagaData>  ──holds reference to──►  CreateOrderSaga
       │
       │  Spring injects this bean when you write
       ▼
  OrderService(..., SagaManager<CreateOrderSagaData> createOrderSagaManager)

At runtime (line 51):
  createOrderSagaManager.create(sagaData, ...)
       │
       │  inside: manager uses its reference to CreateOrderSaga
       ▼
  createOrderSaga.getSagaDefinition()   →  then runs steps by calling
  createOrderSaga.createTicket(data), createOrderSaga.authorizeCard(data), etc.
```

---

## 3. Saga Execution – Full Step-by-Step Diagram

The **SagaManager** uses `CreateOrderSaga.getSagaDefinition()` and runs the steps. Flow:

```
SagaManager (Eventuate Tram)
  │
  │  Loads CreateOrderSagaData from saga_instance
  │  Executes steps in order; on failure, runs compensations in reverse
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 0  (compensation-only)                                                │
│  No forward action. If any later step fails, this compensation runs last.  │
│  Compensation: rejectOrder(data) → CommandWithDestination(orderService,    │
│                "RejectOrder", RejectOrderCommand)                            │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 1  Create Ticket (compensatable)                                       │
│  Forward: createTicket(data) → CommandWithDestination(kitchenService,      │
│           "CreateTicket", CreateTicketCommand)                               │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  │  Message sent to Kafka → kitchenService
  ▼
KitchenSagaCommandHandler.handleCreateTicket(CommandMessage<CreateTicketCommand>)
  │
  ├─► (optional) if saga.kitchen.simulate-failure → throw → saga fails → compensate
  └─► KitchenCommandService.createTicket(cmd)
        │  Ticket.create(...); ticketRepository.save(ticket);
        └─► return CreateTicketReply(ticketId, orderId)
  │
  │  Reply sent back to saga (Kafka)
  ▼
SagaManager receives CreateTicketReply
  │
  ├─► onReply(CreateTicketReply.class, (data, reply) -> data.setTicketId(reply.getTicketId()))
  │     → saga data now has ticketId for later compensation
  │
  └─► If Step 1 failed → run compensation: cancelTicket(data) → see §4
  │
  │  Step 1 success → continue
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 2  Authorize Card (pivot)                                              │
│  Forward: authorizeCard(data) → CommandWithDestination(accountingService,   │
│           "AuthorizeCard", AuthorizeCardCommand)                            │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  │  Message sent to Kafka → accountingService
  ▼
AccountingSagaCommandHandler.handleAuthorizeCard(CommandMessage<AuthorizeCardCommand>)
  │
  ├─► if failOnNegativeTotal && total < 0
  │     → throw RuntimeException → saga fails → compensate (Step 1, then Step 0)
  │
  └─► else
        return withSuccess(CardAuthorizedReply(orderId, authorizationId))
  │
  │  Reply sent back to saga
  ▼
SagaManager receives success
  │
  └─► Step 2 success → continue
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 3  Approve Order (local)                                               │
│  invokeLocal: approveOrder(data)                                            │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  ▼
CreateOrderSaga.approveOrder(data)
  │
  ├─► orderRepository.findById(data.getOrderId())
  ├─► order.approve()   // state → APPROVED
  └─► orderRepository.save(order)
  │
  ▼
Saga completes successfully. Order is APPROVED.
```

**Files:** `CreateOrderSaga` (step definitions), `KitchenSagaCommandHandler`, `KitchenCommandService`, `AccountingSagaCommandHandler`, `CreateOrderSaga.approveOrder()`.

---

## 4. Compensation Flow (e.g. Step 2 Fails – Negative Total)

When Accounting throws (e.g. `orderTotal < 0`), the framework runs compensations **in reverse order** of completed steps:

```
SagaManager detects failure (e.g. exception from accountingService)
  │
  │  Ran: Step 1 (Create Ticket) ✓  →  must compensate Step 1, then Step 0
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  COMPENSATION for Step 1: cancelTicket(data)                                 │
│  CommandWithDestination(kitchenService, "CancelCreateTicket",               │
│    CancelCreateTicketCommand(ticketId, orderId))                             │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  │  Message sent to Kafka → kitchenService
  ▼
KitchenSagaCommandHandler.handleCancelTicket(CommandMessage<CancelCreateTicketCommand>)
  │
  └─► KitchenCommandService.cancelTicket(cmd)
        │  ticket.cancel(); ticketRepository.save(ticket);
        └─► return withSuccess(...)
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  COMPENSATION for Step 0: rejectOrder(data)                                 │
│  CommandWithDestination(orderService, "RejectOrder", RejectOrderCommand)    │
└─────────────────────────────────────────────────────────────────────────────┘
  │
  │  Message sent to Kafka → orderService
  ▼
OrderCommandHandler.rejectOrder(CommandMessage<RejectOrderCommand>)
  │
  └─► order.reject(); orderRepository.save(order)   // state → REJECTED
  │
  ▼
Saga finished (compensated). Order is REJECTED.
```

**Files:** `CreateOrderSaga.cancelTicket` / `rejectOrder` (build commands), `KitchenSagaCommandHandler.handleCancelTicket`, `KitchenCommandService.cancelTicket`, `OrderCommandHandler.rejectOrder`.

---

## 5. Flow Summary by Component

| Phase | Component | What runs |
|-------|-----------|-----------|
| **Request** | `OrderController` | `OrderService.createOrder(...)` |
| **First TX** | `OrderService` | Create Order (PENDING), build `CreateOrderSagaData`, `SagaManager.create(...)` |
| **Saga drive** | Eventuate Tram `SagaManager` | Loads saga definition, sends commands to Kafka, handles replies, runs local step, runs compensations on failure |
| **Step 1** | `CreateOrderSaga.createTicket(data)` | Builds `CreateTicketCommand` → Kafka |
| **Step 1** | `KitchenSagaCommandHandler` | `KitchenCommandService.createTicket`, returns `CreateTicketReply` |
| **Reply** | SagaManager + `onReply` | `data.setTicketId(reply.getTicketId())` |
| **Step 2** | `CreateOrderSaga.authorizeCard(data)` | Builds `AuthorizeCardCommand` → Kafka |
| **Step 2** | `AccountingSagaCommandHandler` | Authorize or throw (e.g. total &lt; 0) |
| **Step 3** | `CreateOrderSaga.approveOrder(data)` | `orderRepository.findById`, `order.approve()`, `save` |
| **Compensate 1** | `CreateOrderSaga.cancelTicket(data)` | Builds `CancelCreateTicketCommand` → Kafka |
| **Compensate 1** | `KitchenSagaCommandHandler` | `KitchenCommandService.cancelTicket` |
| **Compensate 0** | `CreateOrderSaga.rejectOrder(data)` | Builds `RejectOrderCommand` → Kafka |
| **Compensate 0** | `OrderCommandHandler` | `order.reject()`, `save` |

---

## 6. File Reference

| Flow | File |
|------|------|
| REST entry | `ch4-order-service/.../api/OrderController.java` |
| Create order + start saga | `ch4-order-service/.../service/OrderService.java` |
| Saga definition | `ch4-order-service/.../saga/CreateOrderSaga.java` |
| Saga data | `ch4-order-service/.../saga/CreateOrderSagaData.java` |
| Reject order (compensation) | `ch4-order-service/.../command/OrderCommandHandler.java` |
| Create / cancel ticket | `ch4-kitchen-service/.../saga/KitchenSagaCommandHandler.java` |
| Kitchen business logic | `ch4-kitchen-service/.../service/KitchenCommandService.java` |
| Authorize card (pivot) | `ch4-accounting-service/.../saga/AccountingSagaCommandHandler.java` |
