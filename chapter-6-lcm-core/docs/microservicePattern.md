# Chapter 6 & VNFM Project – One‑Page Summary

## **Core Concepts Learned**

### **Event Sourcing**
- Instead of storing current state, store every state change as an **immutable domain event**.
- The current state of an aggregate is derived by **replaying all its events** in order.
- Benefits: full audit trail, temporal queries, reliable event publishing, natural fit for sagas.

### **Aggregates**
- A cluster of domain objects treated as a unit; the root enforces invariants.
- In VNFM: `VnfAggregate` represents a VNF instance; `VnfLcmOpOccAggregate` represents an operation occurrence.
- References between aggregates are by ID, not object references (loose coupling).

### **Command‑Event Separation (`process` / `apply`)**
- `process(command)` validates and returns new events (no state change).
- `apply(event)` updates the aggregate’s state (never fails).
- Enables event‑driven design and replay safety.

### **Optimistic Locking**
- Each aggregate instance has a `version` (number of events).
- When saving new events, the event store checks that the version matches the expected value.
- Database unique constraint `(aggregate_id, version)` prevents duplicates.
- In VNFM: implemented in `JdbcEventStore` and enforced via `UNIQUE` constraint.

### **Snapshots**
- Periodically save the aggregate’s current state to avoid replaying all events.
- Improves performance for long‑lived aggregates.
- VNFM: optional snapshot table (`snapshots`) can be added.

### **Upcasting**
- When event schemas evolve, transform older events to the current format on load.
- Keeps aggregates clean and avoids modifying stored events.

### **Outbox Pattern**
- To reliably send commands/messages, write them to an `outbox` table **in the same transaction** as domain events.
- A separate forwarder (polling scheduler or Debezium) publishes them to Kafka.
- Ensures at‑least‑once delivery without coupling business logic to broker availability.
- VNFM: `OutboxForwarder` (polling) and optional Debezium listener.

### **Idempotency**
- Duplicate requests (e.g., NFVO retries) should not cause duplicate operations.
- Use `requestId` stored in `processed_requests` table; return cached response on duplicate.
- Implemented via Spring `IdempotencyFilter`.

### **Saga Patterns**
- **Choreography**: services react to events; no central coordinator. Simple but less visibility.
- **Orchestration** (used in VNFM): central saga orchestrator (`SagaOrchestrator`) manages steps, sends commands, handles replies, and triggers compensation.
- **Saga state** persisted in `saga_instances` table (saga_id, current_step, status, state JSON).
- **Persistent timeouts** stored in `saga_timeouts` to handle missing replies after pod restarts.
- **Compensation** logic in orchestrator; e.g., if step 2 fails, send `ReleaseResources` command.

### **Debezium / Change Data Capture (CDC)**
- Alternative to polling outbox: use Debezium to tail transaction log and publish outbox rows to Kafka.
- Near‑real‑time, low‑impact, scalable.
- VNFM: can be enabled via configuration (`outbox.mode=cdc`).

### **ETSI Compliance**
- Aligned with SOL002/003: two‑step flow (create instance → instantiate), operation occurrences, 202 Accepted with Location header.
- Internal events and aggregates map to ETSI concepts.

---

## **Checklist – Concepts I Have Mastered**

### **Event Sourcing**
- [ ] I understand why event sourcing is used (audit, reliable events, temporal queries).
- [ ] I can implement an event‑sourced aggregate with `process`/`apply`.
- [ ] I know how optimistic locking works with version and unique constraint.
- [ ] I understand snapshots and when to use them.
- [ ] I know how to handle event schema evolution with upcasting.

### **Saga Orchestration**
- [ ] I can distinguish choreography from orchestration.
- [ ] I have implemented a saga orchestrator with step logic and compensation.
- [ ] I know how to persist saga state and why it’s needed.
- [ ] I understand persistent timeouts and how to implement them.
- [ ] I can handle replies and advance/compensate the saga.

### **Reliable Messaging**
- [ ] I have implemented the outbox pattern (polling scheduler).
- [ ] I understand how to use Debezium as an alternative.
- [ ] I know how to make consumers idempotent (e.g., `processed_replies` table).

### **Idempotency**
- [ ] I can implement an idempotency filter for HTTP APIs using a `processed_requests` table.
- [ ] I understand the trade‑offs of caching responses.

### **DDD & Aggregates**
- [ ] I can identify aggregates in a domain.
- [ ] I know how to design aggregates with proper boundaries.
- [ ] I can implement an aggregate with event sourcing.

### **Production Considerations**
- [ ] I understand the need for atomic transactions across multiple tables.
- [ ] I can configure conditional activation of different outbox mechanisms.
- [ ] I know how to integrate with Kafka and Debezium.
- [ ] I can explain the end‑to‑end flow of a VNF instantiation (happy path and failure compensation).

### **Overall System Design**
- [ ] I can articulate why event sourcing + saga orchestration was chosen for the VNFM.
- [ ] I can discuss alternatives (ORM+outbox, choreography) and their trade‑offs.
- [ ] I can present this design in an interview, connecting patterns to real‑world telecom requirements.

---

*This summary captures the essence of Chapter 6 and your VNFM implementation. Use it for quick revision and interview preparation.*