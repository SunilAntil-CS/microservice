# Chapter 6 – LCM Core – Flow and Architecture

## 1. Project Overview

This project implements the **LCM (Lifecycle Management) Core** component in a VNF (Virtualized Network Function) orchestration stack. The high-level architecture follows the ETSI NFV model and flows as follows:

- **NFVO (Network Function Virtualization Orchestrator)** – Top-level orchestrator. It receives NS/VNF lifecycle requests (e.g. instantiate, scale, terminate), decides *what* should run where, and delegates VNF lifecycle operations to the VNFM.

- **LCM (VNF Lifecycle Manager)** – This component. It receives lifecycle operations from the NFVO (e.g. via northbound API or events), maintains VNF instance state, coordinates the steps of each operation (e.g. create resource, configure, start), and talks to the VIM layer to realize the actual compute/network/storage resources.

- **VIM Manager (VIM Adapter / VIM Driver)** – Abstraction over one or more Virtualised Infrastructure Managers (e.g. OpenStack, VMware). The LCM sends resource requests (e.g. create VM, attach volume) to the VIM Manager; the VIM Manager translates them into provider-specific calls and returns results or callbacks (e.g. deployment finished, failed).

**Goal of the project:** Provide a dedicated, event-driven LCM core that sits between the NFVO and the VIM layer, with clear boundaries and integration points (REST, Kafka), persistent state in PostgreSQL, and readiness for scaling and observability (e.g. Actuator, structured flows documented in this file).

```
    NFVO  →  LCM (this project)  →  VIM Manager  →  VIM (OpenStack, etc.)
```

Further sections in this document will detail request/response flows, state machines, and integration patterns (e.g. Kafka topics, API contracts).

---

## 2. Domain Events and Commands

The LCM core models the VNF lifecycle with **commands** (intent to act) and **domain events** (facts that something happened). All events implement `DomainEvent` and carry `eventId`, `aggregateId` (the VNF identifier), `version`, and `timestamp`. All commands implement the marker interface `Command` and include a `requestId` for idempotency (duplicate requests can be detected and ignored).

### Domain events (`com.vnfm.lcm.domain.event`)

| Event | Purpose | Key fields | State change |
|-------|---------|------------|--------------|
| **VnfInstantiationStarted** | Instantiation has been initiated; resources have been requested from the VIM. | `vnfId`, `resources` | VNF moves from “requested” to “instantiating”. |
| **VnfInstantiated** | The VNF is running and reachable. | `vnfId`, `vimResourceId`, `ipAddress` | VNF is “instantiated” / running. |
| **VnfInstantiationFailed** | Instantiation could not be completed. | `vnfId`, `reason` | VNF remains or returns to a failed/not-instantiated state. |
| **VnfTerminationStarted** | Termination has been initiated (e.g. release requested from VIM). | `vnfId` | VNF moves from “running” to “terminating”. |
| **VnfTerminated** | The VNF has been fully removed. | `vnfId` | VNF is “terminated”; resources released. |
| **VnfTerminationFailed** | Termination could not be completed (e.g. VIM error). | `vnfId`, `reason` | VNF may remain in “terminating” or an error state. |

Each event includes a `version` (schema/contract version) and a `timestamp` (set at construction), and inherits `getEventId()` and `getAggregateId()` from `DomainEvent` (aggregateId is the VNF instance id).

### Commands (`com.vnfm.lcm.domain.command`)

| Command | Purpose | Key fields | Idempotency |
|---------|---------|------------|--------------|
| **InstantiateVnfCommand** | Request creation and start of a VNF with given resources. | `vnfId`, `vnfType`, `cpuCores`, `memoryGb`, `requestId` | Handlers can use `requestId` to avoid applying the same instantiation twice. |
| **TerminateVnfCommand** | Request shutdown and release of an existing VNF. | `vnfId`, `requestId` | Handlers can use `requestId` to avoid applying the same termination twice. |

### How they represent state changes

- **Instantiation flow:** A command `InstantiateVnfCommand` is received → LCM may emit **VnfInstantiationStarted** (and request resources from the VIM) → on success it emits **VnfInstantiated** (with `vimResourceId` and `ipAddress`); on failure it emits **VnfInstantiationFailed** (with `reason`).
- **Termination flow:** A command `TerminateVnfCommand` is received → LCM may emit **VnfTerminationStarted** → on success it emits **VnfTerminated**; on failure it emits **VnfTerminationFailed** (with `reason`).

Events are the source of truth for what happened; commands are the trigger. Downstream consumers (e.g. NFVO, audit, metrics) can subscribe to events to reflect the current VNF lifecycle state without depending on the command channel.

---

## 3. VnfAggregate

The **VnfAggregate** (`com.vnfm.lcm.domain.model.VnfAggregate`) is the domain object that represents a single VNF instance. It holds the current **state** (via the `VnfState` enum: `INITIAL`, `INSTANTIATING`, `ACTIVE`, `TERMINATING`, `TERMINATED`, `FAILED`), the **version** (for optimistic locking), and optional data such as `vimResourceId` and `ipAddress` once the VNF is active.

### Role of the aggregate

- **Single place for VNF state:** All decisions and state changes for one VNF go through this aggregate. It ensures that only valid transitions happen (e.g. you cannot instantiate when already `ACTIVE`).
- **Process commands, apply events:** Commands (e.g. `InstantiateVnfCommand`) are **processed** to produce new domain events; events are **applied** to update the aggregate’s state and version. The aggregate does not persist itself; the application layer loads it from stored events, runs process/apply, and persists new events.

### Process / apply pattern

- **`process(Command)`** – Validates the command against current state and returns a **list of new domain events** (e.g. `process(InstantiateVnfCommand)` returns a list containing `VnfInstantiationStarted`). The aggregate state is **not** changed in process; only the new events are returned. The application will persist these events and then apply them (or reload and apply) to update state.
- **`apply(Event)`** – Updates the aggregate’s fields (state, version, vimResourceId, ipAddress, etc.) according to the event. Each `apply` method is responsible for one event type (e.g. `apply(VnfInstantiated)` sets state to `ACTIVE` and stores `vimResourceId` and `ipAddress`). Apply is used both when replaying an event stream and when applying newly produced events after persistence.

So: **commands in, events out** (process); **events in, state updated** (apply). This keeps the aggregate side-effect-free when processing commands and makes it easy to rebuild state from an event log.

### Rebuilding from events

- **`VnfAggregate.from(List<DomainEvent> events)`** – Reconstructs the aggregate by starting from an empty state (no-args constructor) and **applying each event in order**. Used when loading from the event store or when building the current state from persisted events. The result has the same state and version as if all events had been applied in sequence.

### Version and optimistic locking

- Every applied event carries a **version** (e.g. 1, 2, 3, …). The aggregate’s `version` field is set to the event’s version after each apply.
- When applying, the aggregate checks that the event’s version is **exactly `currentVersion + 1`**. This enforces ordering and detects concurrent or duplicate application. When persisting events, the store can use the same version for optimistic locking (e.g. reject a new event if the aggregate’s version in the store has already moved past it).
- When **processing** a command, the new event is emitted with version **`version + 1`**, so after that event is persisted and applied, the aggregate’s version advances by one.

Together, the aggregate, process/apply, and versioning support event-sourced style behaviour and safe concurrent updates via optimistic locking.

---

## 4. Event Store

The **event store** (`com.vnfm.lcm.infrastructure.eventstore`) is where domain events are persisted and loaded per aggregate. It provides the persistence layer for event-sourced aggregates.

### Interface and implementation

- **`EventStore`** – Interface with:
  - **`saveEvents(UUID aggregateId, String aggregateType, List<DomainEvent> events, int expectedVersion)`** – Appends events for an aggregate. `aggregateType` is `"VNF"` or `"OP_OCC"`. Fails with `OptimisticLockingException` if the aggregate's current version in the store is not equal to `expectedVersion`.
  - **`loadEvents(UUID aggregateId, String aggregateType)`** – Returns all events for the aggregate in **version order** (ascending), so they can be replayed to rebuild state (e.g. `VnfAggregate.from(events)` or `VnfLcmOpOccAggregate.from(events)`).
  - **`getLatestSnapshot(UUID aggregateId, String aggregateType)`** – Returns the latest snapshot for the aggregate, if any (see below).

- **`JdbcEventStore`** – JPA-based implementation. Uses **`EventEntity`** (mapped to table `events`) and **`SnapshotEntity`** (table `snapshots`). Events are serialized to JSON (with `event_type` and payload) and stored in the `events` table. Each row has `event_id` (unique), `aggregate_id`, `version`, `event_type`, `payload`, and `event_timestamp`.

### How events are persisted

1. The application produces new events (e.g. from `VnfAggregate.process(command)`).
2. It calls **`saveEvents(aggregateId, aggregateType, events, expectedVersion)`**, where `expectedVersion` is the aggregate’s version *before* these new events (e.g. 0 for the first save, 2 after two events are already stored).
3. The store **checks the current max version** for that aggregate in the DB. If `maxVersion != expectedVersion`, it throws **`OptimisticLockingException`** and does not insert (concurrent or out-of-order write).
4. If the check passes, it **inserts each event** with version `expectedVersion + 1`, `expectedVersion + 2`, … and serializes each event (e.g. to JSON) into the `payload` column.

Loading replays the stream: **`loadEvents(aggregateId, aggregateType)`** returns rows ordered by `version`, and the client deserializes each payload back into a `DomainEvent` and can apply them (e.g. `VnfAggregate.from(events)` or `VnfLcmOpOccAggregate.from(events)`). The **`events`** table includes an **`aggregate_type`** column (`VNF` or `OP_OCC`) to distinguish streams.

### Role of version for optimistic locking

- **Expected version** – The caller (e.g. application service) loads the aggregate from the store (or from a snapshot + following events), gets its current `version`, and when saving new events passes that as `expectedVersion`.
- **Max version in store** – Before inserting, the store runs a query like `SELECT MAX(version) FROM events WHERE aggregate_id = ?`. If another writer has already appended an event, `maxVersion` will be higher than `expectedVersion`, and the store throws **`OptimisticLockingException`**, so the caller can retry (reload, process again, save again) or report a conflict.
- This prevents lost updates and enforces a single, ordered event stream per aggregate without locking the row for the whole transaction.

### Snapshot mechanism

- **Snapshots** are optional stored states of an aggregate at a given **version** (e.g. “aggregate X at version 10”). They are stored in the **`snapshots`** table (`aggregate_id`, `version`, `payload`, `created_at`).
- **`getLatestSnapshot(aggregateId)`** – Returns the snapshot with the highest version for that aggregate, if any.
- **Purpose** – To speed up rebuilds: instead of replaying all events from version 1, the application can load the latest snapshot (e.g. state at version 10), then load only events with `version > 10` and apply those. The snapshot is an optimization; the source of truth remains the event stream.
- Snapshot creation and update are not part of the core `EventStore` interface in this design; they can be implemented by a separate process or when loading (e.g. “if event count > N, persist a snapshot”).


---

## 5. Outbox Pattern

The **outbox pattern** ensures that commands (or events) destined for external systems (e.g. Kafka) are **reliably sent** without losing messages or breaking transactional consistency with the primary database.

### Why use an outbox?

- **Problem:** When the application writes to the database and then sends a message to Kafka in two separate steps, a crash after the DB commit but before the Kafka send results in a **lost message**. Conversely, sending to Kafka first and then writing to the DB can leave Kafka with a message that never gets reflected in the DB.
- **Solution:** Write the **outgoing message into a dedicated outbox table in the same transaction** as the business write. Then a **separate process** (the **OutboxForwarder**) periodically reads from the outbox and publishes to Kafka. If the forwarder fails, the message remains in the DB and can be retried.

So: **one transaction** = business data + outbox row. No message is lost as long as the transaction commits.

### How it works in this project

1. **Writing to the outbox**  
   When the application produces a command that must be sent to a downstream system (e.g. VIM Manager), it **inserts a row into the `outbox` table** in the **same transaction** as the domain write (e.g. saving events). The row contains: `message_id`, `destination`, `message_type`, `payload` (JSON), `status` (e.g. `PENDING`), `retry_count`, `created_at`, `next_retry_at`, `last_error`.

2. **OutboxForwarder (scheduled job)**  
   A scheduled component runs **every 5 seconds** (configurable via `lcm.outbox.forwarder.fixed-delay`). It:
   - Fetches all rows where `status = 'PENDING'` and `next_retry_at <= now()` (messages due for send or retry).
   - For each message, calls **MessagePublisher** (e.g. **KafkaMessagePublisher**) to send the payload to the appropriate Kafka topic (e.g. destination `vim.manager` → topic `vim.commands`).
   - **On success:** sets `status = 'SENT'` and saves the row (no further retries).
   - **On failure:** increments `retry_count`, sets `last_error`, and sets `next_retry_at = now() + exponential backoff (e.g. `2 * 2^retry_count` seconds), then saves. The message stays `PENDING` and will be picked up again on a later run.

3. **Retries and exponential backoff**  
   If Kafka (or the broker) is temporarily unavailable, the forwarder does **not** remove the message. It updates `retry_count` and `next_retry_at` so that the next attempt happens later (e.g. 2s, 4s, 8s, …). This avoids hammering the broker and gives it time to recover. Messages remain in the outbox until they are successfully published (status `SENT`).

4. **Kafka producer configuration**  
   The application configures the Kafka producer (bootstrap servers, key/value serializers) in `application.yml` under `spring.kafka.producer`. The **KafkaMessagePublisher** uses `KafkaTemplate<String, String>` to send the `message_id` as key and the JSON `payload` as value.

### Summary

- **Outbox table** = durable queue of messages to publish, stored in the same DB as the domain.
- **OutboxForwarder** = scheduled job that reads PENDING messages due for retry and publishes them via **MessagePublisher** (Kafka).
- **Retries** = on publish failure, the message stays PENDING; `retry_count` and `next_retry_at` (exponential backoff) ensure it is retried later without losing the message.


---

## 6. Idempotency

The **northbound API** (REST) enforces **idempotency** so that duplicate requests from the NFVO (e.g. retries after timeouts) do not cause duplicate side effects (e.g. instantiating a VNF twice). The same **requestId** always yields the same response.

### How it works

1. **Request identification**  
   Each request that should be idempotent carries a **requestId** (e.g. UUID). The client can send it in either:
   - The **`X-Request-Id`** HTTP header, or  
   - The request body (e.g. JSON field **`requestId`** for POST/PUT/PATCH).

2. **Idempotency filter**  
   A servlet **filter** (`IdempotencyFilter`) runs for all requests under **`/api/`**:
   - It extracts **requestId** from the header or, for JSON bodies, from the body (without breaking the downstream controller’s ability to read the body).
   - It looks up **requestId** in the **`processed_requests`** table (JPA entity **`ProcessedRequest`**: `requestId`, `responseCache`, `processedAt`).
   - **If a row exists:** the filter returns the cached response (JSON in **`response_cache`**) with status 200 and does **not** call the controller. The client receives the same response as the first time.
   - **If no row exists:** the filter forwards the request to the controller. After the controller returns a **2xx** response, the filter stores the response body in **`processed_requests`** (same transaction as the rest of the app, or immediately after the response is committed) so the next duplicate request will be served from cache.

3. **REST controller**  
   The **VnfLifecycleController** (e.g. **POST /api/v1/vnf/instantiate**) accepts the request body (including **requestId**) and returns a response (e.g. **InstantiateResponse**). The filter does not change the controller’s contract; it only short-circuits when it finds a cached response for that **requestId**.

4. **Summary**  
   - **First request** with a given **requestId**: controller runs, response is cached in **processed_requests**, client gets that response (e.g. 202 Accepted).  
   - **Duplicate request** (same **requestId**): filter finds the cached row, returns the cached JSON with 200 OK; the controller is **not** invoked.  
   - Idempotency is enforced at the **API boundary** so that even if the client retries, the LCM does not perform the same operation twice and returns a consistent response.

---

## 7. Saga Orchestrator

The **saga orchestrator** coordinates **multi-step** VNF lifecycle flows (e.g. instantiation) across the LCM and the VIM. Each step can succeed or fail; on failure, **compensation** (rollback) is run for steps that already succeeded, so the system eventually reaches a consistent state.

### Saga instance (state machine)

A **saga** is one run of a multi-step process. Its state is stored in the **`saga_instances`** table (JPA entity **`SagaInstance`**):

| Field | Purpose |
|-------|--------|
| **sagaId** | Unique id (UUID) for this saga run. |
| **vnfId** | VNF this saga is for. |
| **sagaType** | e.g. `VNF_INSTANTIATE`. |
| **currentStep** | The step the saga is currently on (1, 2, …). |
| **sagaState** | JSON holding completed steps, step results, etc. (used to decide compensation). |
| **status** | `RUNNING`, `COMPENSATING`, `COMPLETED`, or `FAILED`. |
| **createdAt** / **updatedAt** | Timestamps. |

The **SagaOrchestrator** service starts sagas, records replies, and drives the state machine forward or into compensation.

### Instantiation saga flow (simulated)

For **VNF instantiation** the flow is:

1. **Start** – `startInstantiateSaga(vnfId, resources)` creates a new **SagaInstance** (status `RUNNING`, currentStep = 1), saves it, and sends the first command **ReserveResources** (with sagaId, vnfId, resources) via the **outbox** (destination `vim.manager`). No direct VIM call; the outbox forwarder will publish to Kafka. The method returns the **sagaId**.

2. **Step 1: ReserveResources** – The VIM (or a simulator) eventually processes the command and sends a reply. The application calls **handleReply(sagaId, step = 1, success, result)**.  
   - **Success:** The orchestrator records step 1 as completed in **sagaState**, sets **currentStep = 2**. For now, step 2 (e.g. Deploy) is only simulated; no second outbox command is required for this step.  
   - **Failure:** No compensation (step 1 did not complete). Status is set to **FAILED**.

3. **Step 2: Deploy (simulated)** – When a reply for step 2 is received via **handleReply(sagaId, step = 2, success, result)**:  
   - **Success:** Saga status is set to **COMPLETED**.  
   - **Failure:** The orchestrator checks **sagaState**: if step 1 had succeeded, it starts **compensation** by sending a **ReleaseResources** command via the outbox (same destination, payload includes sagaId, vnfId, reason). Then status is set to **FAILED**.

### Compensation

**Compensation** = undoing steps that already succeeded. Here, only **ReserveResources** (step 1) has a compensating action: **ReleaseResources**. So:

- If **step 1 fails** → nothing to compensate; saga goes to **FAILED**.
- If **step 2 fails** and step 1 had succeeded → send **ReleaseResources** via the outbox, then set saga to **FAILED**.

The outbox ensures that **ReleaseResources** is eventually delivered to the VIM (e.g. via Kafka), even if the process restarts after the saga is updated.

### Summary

- **SagaInstance** = one saga run; **saga_instances** table holds sagaId, vnfId, sagaType, currentStep, sagaState (JSON), status, timestamps.
- **SagaOrchestrator** = starts sagas (**startInstantiateSaga**), persists the instance, sends the first command (**ReserveResources**) via the outbox; **handleReply** updates state and either advances to the next step/completes or runs compensation (**ReleaseResources** if step 1 succeeded).
- The flow is **orchestrated** by the LCM: it sends commands (outbox → Kafka) and reacts to replies via **handleReply**; actual VIM interaction and reply handling can be wired later.

---

## 8. Persistent Timeouts

If a saga step never gets a reply (e.g. VIM is down, message lost, or the participant never responds), the saga would otherwise stay in **RUNNING** forever. To avoid that, each step has a **persistent timeout**: a row in the **`saga_timeouts`** table with **executeAt = now() + timeout**. When that time is reached and no reply has been received, the system treats the step as failed and runs compensation (or marks the saga failed) so the flow can terminate consistently.

### Why persistent timeouts?

- **In-memory timers are lost on restart** – If the app crashes or restarts after sending a command but before the reply (or timeout) is handled, an in-memory “schedule timeout in 2 minutes” is gone. The saga would never time out.
- **Persistent timeouts survive restarts** – The timeout is stored in the DB (same transaction as the command / saga update). A **TimeoutScheduler** runs periodically (e.g. every 5 seconds), queries for rows with **processed = false** and **executeAt <= now()**, and for each due timeout loads the saga and, if it is still waiting for that step, triggers the same failure path as a real failure (e.g. **handleReply(sagaId, step, false, reason: "timeout")**). Then it marks the timeout as **processed** so it is not handled again.

So: **durable** timeout records + **scheduled job** that processes them = timeouts that work even after restarts.

### How it works

1. **When a command is sent** – In the same transaction as sending the command (e.g. **ReserveResources** via the outbox), the orchestrator inserts a **SagaTimeout** row: **sagaId**, **step**, **executeAt = now() + timeout** (e.g. 2 minutes), **processed = false**, **createdAt = now()**.

2. **When a reply arrives first** – **handleReply** is called. At the start it marks any pending timeout for this **sagaId + step** as **processed = true** (so the scheduler will ignore it). So “reply wins”: once we’ve recorded the reply, the timeout is effectively cancelled.

3. **When no reply before executeAt** – The **TimeoutScheduler** (e.g. **@Scheduled(fixedDelay = 5000)**) runs periodically. It queries **saga_timeouts** for **processed = false** and **executeAt <= now()**. For each row it loads the **SagaInstance**. If the saga is still **RUNNING** and **currentStep** equals the timeout’s step, it calls **handleReply(sagaId, step, false, Map.of("reason", "timeout"))**, which updates the saga (and may run compensation) and, inside **handleReply**, marks the timeout as processed. If the saga is already completed or failed (e.g. reply arrived on another node or was processed earlier), it just marks the timeout as processed so it is not picked again.

4. **Configuration** – Timeout duration per step is configurable (e.g. **lcm.saga.step-timeout-seconds: 120**). The scheduler interval is also configurable (e.g. **lcm.saga.timeout-scheduler.fixed-delay: 5000**).

### Summary

- **SagaTimeout** = one row per “schedule a timeout for this saga step” (sagaId, step, executeAt, processed, createdAt).
- **Orchestrator** creates a timeout row in the **same transaction** as sending the command; **handleReply** marks the timeout **processed** when a reply is received so the scheduler skips it.
- **TimeoutScheduler** runs on a fixed delay, selects due unprocessed timeouts, and for each: if the saga is still waiting for that step, calls **handleReply** with failure (timeout); then the timeout is marked processed. This ensures sagas eventually complete or fail even when replies are lost or the system restarts.

---

## 10. REST API

The **northbound REST API** exposes VNF lifecycle operations. All endpoints under **/api/** are subject to the **idempotency filter** (see §6): duplicate **requestId** returns the cached response.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| **POST** | /api/vnfs | Instantiate a new VNF. Body: **InstantiateVnfRequest** (requestId, vnfType, cpuCores, memoryGb). Returns **202 Accepted** with **Location: /api/vnfs/{vnfId}/status** and a body with vnfId and statusUrl. |
| **DELETE** | /api/vnfs/{vnfId} | Terminate the VNF. **requestId** via **X-Request-Id** header or **requestId** query param (for idempotency). Returns **202 Accepted**. |
| **GET** | /api/vnfs/{vnfId} | Return the current VNF state (projected from the event store): vnfId, state, version, vimResourceId, ipAddress. |
| **GET** | /api/vnfs/{vnfId}/status | Same as GET /api/vnfs/{vnfId}; used as the status URL in the Location header after POST. |
| **GET** | /api/vnfs | List all VNFs (from the read-side **vnf_index** table; state is projected from the event store per VNF). |

### How the API triggers sagas

- **POST /api/vnfs** – The controller generates a new **vnfId** (UUID), adds it to the **vnf_index** read-side table, and calls **SagaOrchestrator.startInstantiateSaga(vnfId, resources)**. The orchestrator creates a saga instance, sends **ReserveResources** via the outbox, and creates a persistent timeout. The response is returned immediately with **202** and a **Location** header so the client can poll the status endpoint. No direct VIM call in the request path; the outbox forwarder and saga handle the rest asynchronously.

- **DELETE /api/vnfs/{vnfId}** – The controller calls **SagaOrchestrator.startTerminateSaga(vnfId)**. The orchestrator creates a terminate saga and sends **TerminateVnf** via the outbox. Returns **202 Accepted** immediately.

- **GET /api/vnfs/{vnfId}** and **GET /api/vnfs** – Use **VnfQueryService**, which loads events from the **event store** (and, for listing, the **vnf_index** table), rebuilds **VnfAggregate** from events where applicable, and returns the projected state. No saga is started; these are read-only.

### DTOs and read-side

- **InstantiateVnfRequest** / **InstantiateVnfResponse** – Request (requestId, vnfType, cpuCores, memoryGb) and response (vnfId, statusUrl, message).
- **VnfStateResponse** – vnfId, state, version, vimResourceId, ipAddress (from event-store projection).
- **VnfSummary** – vnfId, state (for list).
- **vnf_index** – Read-side table holding known vnf_id values; populated when a VNF is created (POST). Used by GET /api/vnfs to list VNFs; state for each is then projected from the event store.

---

## 11. Debezium CDC

**Change Data Capture (CDC)** allows the LCM to reliably publish database changes to Kafka without coupling the write path to the messaging layer. Using an **embedded Debezium engine**, the application captures row-level changes on the **`events`** and **`outbox`** tables and forwards each change to the appropriate Kafka topic. This complements the existing **OutboxForwarder** (see §5) by providing a CDC-based path: every insert or update is streamed from PostgreSQL’s transaction log to Kafka, so downstream consumers see a consistent, ordered feed of events and outbox messages.

### Why Debezium CDC?

- **Decoupled publishing** – The application writes only to the database (event store, outbox). A separate Debezium engine reads from the database log and publishes to Kafka. If Kafka is temporarily down, no application transaction fails; changes are replayed when the engine catches up.
- **Single source of truth** – The database is the durable store. Kafka topics are derived from the same data via CDC, so there is no risk of the application “forgetting” to publish an event.
- **Embedded engine** – For simplicity, the LCM uses the **embedded Debezium engine** (no separate Kafka Connect cluster). The engine runs inside the application JVM, in a dedicated thread, and is managed by Spring’s lifecycle (`@PostConstruct` to start, `@PreDestroy` to stop).

### How it works

1. **DebeziumListener** – A Spring component (`com.vnfm.lcm.infrastructure.debezium.DebeziumListener`) starts when the application starts (if `lcm.debezium.enabled` is `true`). It configures a Debezium **PostgreSQL connector** with:
   - Database connection (hostname, port, user, password, dbname) from **`lcm.debezium.database`** in `application.yml`.
   - **`table.include.list`** = `public.events,public.outbox` (configurable via **`lcm.debezium.table-include-list`**).
   - **`plugin.name`** = `pgoutput` (PostgreSQL’s built-in logical decoding plugin; no extra install).
   - A replication slot and publication (e.g. **`lcm_cdc_slot`**, **`lcm_cdc_pub`**) so the connector can stream changes. The database must have **logical replication** enabled (`wal_level=logical`) and, depending on setup, the publication may need to be created manually (e.g. `CREATE PUBLICATION lcm_cdc_pub FOR TABLE events, outbox;`).

2. **Engine thread** – The Debezium engine runs in a **separate thread** (e.g. `debezium-cdc-engine`). It does not block the main application. On shutdown, **`@PreDestroy`** closes the engine and the executor so the JVM can exit cleanly.

3. **Change events** – For each captured change (insert/update), the engine invokes a **consumer** with a JSON payload (Debezium envelope: `source`, `after`, `before`, `op`, etc.). The **DebeziumListener** parses this payload to determine:
   - **Table name** (from `source.table`): `events` or `outbox`.
   - **Topic and key**:
     - **`events` table** → Kafka topic **`vnf.events`** (configurable via **`lcm.debezium.events-topic`**). Message key = **`event_id`** from the row.
     - **`outbox` table** → Kafka topic = row’s **`destination`** column (e.g. **`vim.commands`**). Message key = **`message_id`** from the row.

4. **EventPublisher** – A simple component (**`EventPublisher`**) uses **`KafkaTemplate<String, String>`** to send each change to the chosen topic. The **value** is the full Debezium JSON envelope (so consumers get metadata and the full row state). Thus, inserting a row in **`events`** results in a message on **`vnf.events`**; inserting a row in **`outbox`** results in a message on the topic specified by that row’s **`destination`** (e.g. **`vim.commands`**).

### Configuration summary

| Property | Description |
|----------|-------------|
| **lcm.debezium.enabled** | If `true`, the DebeziumListener starts the embedded engine (default: `true`). |
| **lcm.debezium.database.*** | hostname, port, username, password, dbname for the PostgreSQL connection. |
| **lcm.debezium.table-include-list** | Comma-separated list of tables, e.g. `public.events,public.outbox`. |
| **lcm.debezium.events-topic** | Kafka topic for changes from the **events** table (default: **vnf.events**). |
| **lcm.debezium.connector-name** | Connector name used for offset storage and logging. |

### Relation to the Outbox pattern

- **OutboxForwarder** (§5) polls the **`outbox`** table and publishes **PENDING** rows to Kafka, then marks them **SENT**. It is the primary mechanism for **commands** (e.g. ReserveResources) that must be delivered to the VIM.
- **Debezium CDC** streams **all** changes to **`events`** and **`outbox`** (inserts and updates) to Kafka. Downstream systems can consume **`vnf.events`** for domain events and the outbox topic (e.g. **`vim.commands`**) for commands. CDC does not replace the OutboxForwarder’s status updates (PENDING → SENT); it provides an additional, log-based view of the same data. In production you may choose one path (e.g. outbox forwarder only, or CDC only for events and outbox) or both, depending on consistency and operational requirements.

### Summary

- **DebeziumListener** = starts the embedded Debezium engine for PostgreSQL, captures **events** and **outbox**, runs in a separate thread, managed by `@PostConstruct` / `@PreDestroy`.
- **EventPublisher** = forwards each change to Kafka via **KafkaTemplate** (topic and key derived from table and row).
- **Events table** → **vnf.events**; **outbox table** → topic from **destination** column (e.g. **vim.commands**).
- Configuration under **lcm.debezium** in **application.yml**; database must support logical replication (e.g. **wal_level=logical**) and a publication for the captured tables.

---

## 12. ETSI-Compliant Northbound API

The **ETSI SOL002/003** compliant API is exposed under **`/vnflcm/v1`**. It implements a **two-step instantiation flow** and **operation occurrence** tracking as in the standard.

### Two-step flow

1. **Create VNF instance** – **POST /vnflcm/v1/vnf_instances** with optional body (vnfInstanceName, vnfInstanceDescription). The server creates a new VNF aggregate with a **VnfInstanceCreated** event (state **NOT_INSTANTIATED**), persists it, and adds the id to the **vnf_index** read-side. Returns **201 Created** with **Location: /vnflcm/v1/vnf_instances/{vnfId}** and a **VnfInstance** body.

2. **Start instantiation** – **POST /vnflcm/v1/vnf_instances/{vnfId}/instantiate** with body (flavourId, instantiationLevelId, extVirtualLinks, optional requestId). The server:
   - Verifies the VNF exists and is in NOT_INSTANTIATED state.
   - Emits **VnfInstantiationStarted** on the VNF aggregate (state moves to INSTANTIATING).
   - Creates an **operation occurrence** aggregate (**VnfLcmOpOccAggregate**) with state **STARTING** (event **OpOccCreated**).
   - Starts the **saga** for instantiation (ReserveResources, etc.), passing **operationId** so the saga can update the operation occurrence on completion or failure.
   - Returns **202 Accepted** with **Location: /vnflcm/v1/vnf_lcm_op_occs/{operationId}** (no body).

The client can poll **GET /vnflcm/v1/vnf_lcm_op_occs/{opId}** to see the operation state (STARTING, PROCESSING, COMPLETED, FAILED).

### Operation occurrences

- **VnfLcmOpOccAggregate** – Event-sourced aggregate for each LCM operation (e.g. INSTANTIATE, TERMINATE). Events: **OpOccCreated**, **OpOccUpdated**, **OpOccCompleted**, **OpOccFailed**. State values: STARTING, PROCESSING, COMPLETED, FAILED, ROLLING_BACK.
- Stored in the same **events** table with **aggregate_type = 'OP_OCC'** (aggregate_id = operation occurrence UUID).
- When the **SagaOrchestrator** completes or fails an instantiation saga, it loads the corresponding **VnfLcmOpOccAggregate** (using the **operation_id** stored on the saga instance), emits **OpOccCompleted** or **OpOccFailed**, and saves the event in the same transaction as the saga update.

### Endpoints (ETSI)

| Method | Path | Description |
|--------|------|-------------|
| **POST** | /vnflcm/v1/vnf_instances | Create a VNF instance. Body: **CreateVnfInstanceRequest** (optional vnfInstanceName, vnfInstanceDescription). Returns **201** with **Location** and **VnfInstance**. |
| **POST** | /vnflcm/v1/vnf_instances/{vnfId}/instantiate | Start instantiation. Body: **InstantiateVnfRequestLcm** (flavourId, instantiationLevelId, extVirtualLinks, optional requestId). Header **X-Request-ID** for idempotency. Returns **202** with **Location: /vnflcm/v1/vnf_lcm_op_occs/{opId}**. |
| **GET** | /vnflcm/v1/vnf_instances/{vnfId} | Return **VnfInstance** (id, instantiationState, vnfInstanceName, vimResourceId, ipAddress, etc.). |
| **GET** | /vnflcm/v1/vnf_instances | List all **VnfInstance** (from vnf_index + event-store projection). |
| **GET** | /vnflcm/v1/vnf_lcm_op_occs/{opId} | Return **VnfLcmOpOcc** (id, operation, state, vnfInstanceId, startTime, endTime, error). |

### How internal patterns support this flow

- **Event sourcing** – Both **VnfAggregate** and **VnfLcmOpOccAggregate** use the same **EventStore**; **aggregate_type** distinguishes VNF vs OP_OCC streams.
- **Saga orchestration** – **SagaOrchestrator.startInstantiateSaga(vnfId, operationId, resources)** stores **operation_id** on the saga instance. On **handleReply** (success or failure), the orchestrator updates the operation occurrence aggregate (OpOccCompleted / OpOccFailed) in the same transactional boundary where applicable.
- **Outbox** – Unchanged; saga commands (e.g. ReserveResources) are still written to the outbox and forwarded to Kafka.
- **Persistent timeouts** – Unchanged; saga steps still have **saga_timeouts** rows; timeouts are tied to the saga (and thus to the operation ID).
- **Idempotency** – **IdempotencyFilter** applies to **POST /vnflcm/v1/vnf_instances** and **POST /vnflcm/v1/vnf_instances/{vnfId}/instantiate**. **X-Request-ID** (or requestId in body for instantiate) is used. For **202** responses, the cached value includes **status** and **Location** header so duplicate requests receive the same 202 and Location.

### Mapping of ETSI resources to aggregates

| ETSI resource | Aggregate / store |
|---------------|-------------------|
| VNF Instance | **VnfAggregate** (events with aggregate_type = VNF); first event **VnfInstanceCreated** for creation. |
| LCM Operation Occurrence | **VnfLcmOpOccAggregate** (events with aggregate_type = OP_OCC). |
| List VNF Instances | **vnf_index** read-side + event-store projection per vnfId. |
