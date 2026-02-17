# Interview Prep: VNFM DDD + Saga (LCM & VIM Adapter)

This document supports a **Senior Java Developer** interview with a **Principal Software Architect** perspective in the Telecom domain. It explains the production-grade VNFM (Virtual Network Function Manager) example that uses **Domain-Driven Design** and the **Saga/Transactional Outbox** pattern across two microservices.

---

## 1. Business Flow Between the Two Microservices

### Context

The VNFM operates on the **Management Plane** of a telecom network (e.g., orchestrating Cisco UCS M8 servers for a 15M-subscriber network). The overall network may handle 40k TPS on the Control/Data Plane, but **this orchestration process favors strict data consistency over microsecond latency**.

### The Two Aggregates

| Service              | Aggregate       | Responsibility                                      | Database   |
|----------------------|-----------------|------------------------------------------------------|------------|
| **LCM Service**      | `VnfInstance`   | Lifecycle state of the VNF (e.g. DEPLOYING_INFRA → ACTIVE) | LCM DB     |
| **VIM Adapter**      | `CloudDeployment` | Actual infrastructure (e.g. Kubernetes pods, VMs)   | VIM DB     |

They **reference each other only by String id** (`vnfId`, `deploymentId`). No shared tables, no distributed transaction.

### End-to-End Flow (Full Saga: Forward + Return Trip)

**Forward (LCM → VIM)**

1. **Operator/API** calls LCM: “Instantiate VNF `vnf-001` with 8 vCPU, 16 GB RAM.”
2. **LCM Service** (single transaction):
   - Creates `VnfInstance` in state `DEPLOYING_INFRA` (rich domain: `VnfInstance.requestDeployment(...)`).
   - Persists the aggregate.
   - Writes `InfraDeploymentRequestedEvent` to the **outbox table** in the **same** transaction (Transactional Outbox).
3. **Commit**: Both the `vnf_instances` row and the outbox row are committed together. No Kafka call yet.
4. **LCM Outbox Relay** (Phase 4): Scheduled job reads the outbox, publishes the event to Kafka topic `infra-deployment-requested` (with `message_id` header), then marks the row published.
5. **VIM Adapter** consumes the event:
   - **Idempotency check**: Has `eventId` (message id) already been processed? If yes, return.
   - Create `CloudDeployment` with `lastProcessedEventId = eventId` (UNIQUE constraint).
   - Call VIM/Kubernetes API to create pods (or simulate).
   - Mark deployment `RUNNING`, save.
   - **Phase 3 reply**: In the **same** transaction, write `InfraDeployedReplyEvent` (vnfId, deploymentId, SUCCESS) to the **VIM outbox table**. No Kafka call yet.

**Return trip (VIM → LCM)**

6. **VIM Outbox Relay** (Phase 4): Reads VIM’s outbox, publishes the reply payload to Kafka topic `infra-deployed-reply` (with `message_id` header), marks the row published.
7. **LCM Service** (Phase 5 — idempotent consumer):
   - **LcmEventHandler** consumes from `infra-deployed-reply`.
   - **Idempotent shield**: Check `ProcessedMessageRepository.existsById(message_id)`. If already processed, acknowledge and return (no duplicate `markInfraDeployed`).
   - Call **`lcmService.markInfraDeployed(vnfId, deploymentId)`** (updates `VnfInstance` to ACTIVE, sets `deploymentId`).
   - Save **`ProcessedMessageEntity`** with that `message_id` in the **same** `@Transactional` method.
   - **Manually acknowledge** the Kafka message only at the very end (after DB commit), so redelivery does not re-apply the update.

So: **one logical “instantiate VNF” flow** is implemented as **multiple local transactions** (LCM create + outbox; LCM relay → Kafka; VIM consume + create + reply outbox; VIM relay → Kafka; LCM consume + mark deployed + processed_message) coordinated by **events over Kafka**, with **no 2PC** and **strict aggregate boundaries**. The REST endpoint `POST /{vnfId}/mark-deployed` is deprecated; the canonical path is the Kafka reply.

---

## 2. Why the Transactional Outbox Pattern Here?

### The Problem (Dual Write)

We must do two things: (1) update the LCM database (insert `VnfInstance`), and (2) notify the VIM Adapter (send event to Kafka). If we do them in two steps:

- If DB commits but Kafka send fails → VIM never gets the event → **orphan VNF** (LCM thinks “deploying”, VIM never creates pods).
- If we send to Kafka first and then DB fails → **phantom event** (VIM might create pods for a VNF that was never persisted).

So we cannot rely on “commit then send” or “send then commit” without a consistency guarantee.

### The Solution: Outbox in the Same DB

1. In the **same** `@Transactional` method we:
   - `vnfInstanceRepository.save(vnf)`
   - `outboxRepository.save(outboxRow)` where the row contains the event payload and destination topic.
2. So **one** database transaction commits both the business row and the outbox row. Either both succeed or both roll back—**atomicity**.
3. A **separate process** (relay job or CDC) reads the outbox and publishes to Kafka. If that process fails after sending to Kafka but before marking the row “published”, Kafka may get the message again (at-least-once)—which is why the **consumer** must be **idempotent** (see Section 4 for the return-trip consumer, Section 5 for the VIM consumer).

So: **Transactional Outbox** gives us “event is published **only if** the business transaction committed.” No dual write in the application code.

### Why DB-Based Outbox Is Acceptable on the Management Plane (vs 40k TPS)

- **Management Plane** (VNFM, NFVO, OSS): Low frequency (e.g. 10–50 TPS during scaling), long-running workflows, **consistency and reliability** are paramount. A relational DB and a DB-based outbox table are a good fit; the extra writes (entity + outbox) are negligible.
- **Control/Data Plane** (40k TPS): Here, a DB-backed outbox would become a bottleneck (tens of thousands of inserts/updates per second). For that plane you would use different trade-offs (e.g. in-memory stores, different messaging, or accept different consistency models).

So the defense is: **“We use the Transactional Outbox on the Management Plane because we prioritize strict consistency over latency, and the throughput is low enough that a relational outbox is acceptable. We would not use this same pattern on the 40k TPS Control Plane.”**

---

## 3. How @Version Prevents the “Sam and Mary” Concurrent Update Problem

### The Scenario

Two operators (or two API calls) act on the **same** `VnfInstance` at the same time:

- **Sam** loads `VnfInstance` (version = 1), changes state to `ACTIVE`, saves.
- **Mary** loads the same `VnfInstance` (version = 1), changes something else, saves.

Without protection, Mary’s save could **overwrite** Sam’s update (lost update).

### How @Version Works

- The entity has a `@Version` field (e.g. `Long version`). JPA maps it to a column and uses it for **optimistic locking**.
- On **UPDATE**, JPA sends something like:  
  `UPDATE vnf_instances SET state = ?, version = version + 1, ... WHERE vnf_id = ? AND version = ?`
- **Sam** commits first: row now has `version = 2`.
- **Mary**’s update still has `WHERE version = 1`. So **0 rows updated**. JPA throws `OptimisticLockException`.
- The application can retry Mary’s operation (reload aggregate, reapply change, save). So the **second commit** fails in a predictable way instead of silently overwriting.

So: **@Version** gives **optimistic locking** and prevents lost updates when two actors update the same aggregate concurrently.

---

## 4. Return Trip: VIM Outbox (Phase 3–4) and LCM Idempotent Consumer (Phase 5)

### Phase 3: VIM writes reply to outbox (same TX as CloudDeployment)

After the VIM Adapter marks the deployment RUNNING, it must notify the LCM so the Saga can complete. We do **not** call Kafka inside the same transaction as `CloudDeployment` updates. Instead, we write **`InfraDeployedReplyEvent`** (vnfId, deploymentId, status=SUCCESS) to a **VIM outbox table** in the same `@Transactional` method. So: either both the `CloudDeployment` update and the outbox row are committed, or neither (Transactional Outbox on the return path).

### Phase 4: VIM Outbox Relay sends reply to Kafka

A separate **OutboxRelay** in the VIM service (scheduled, e.g. every 2s) reads unpublished rows from the VIM outbox and publishes them to the **reply topic** (`infra-deployed-reply`). It sets the **`message_id`** header (outbox row id) so the LCM consumer can use it for idempotency. On send failure, the row is not marked published and will be retried on the next run (at-least-once).

### Phase 5: LCM consumes reply and completes the Saga

**LcmEventHandler** listens to `infra-deployed-reply`. It must:

- Run in a single **`@Transactional`** so that `markInfraDeployed` and the insert into **`processed_message`** commit together.
- **Check the idempotent shield first**: `ProcessedMessageRepository.existsById(message_id)`. If the reply was already processed (e.g. Kafka redelivery), skip and acknowledge without calling `markInfraDeployed` again.
- Call **`lcmService.markInfraDeployed(vnfId, deploymentId)`** to update `VnfInstance` to ACTIVE.
- **Record** the `message_id` in **`ProcessedMessageEntity`** (same TX).
- **Acknowledge** the Kafka message **only at the very end**. If we ack before recording the processed message and the process crashes, a redelivery could apply `markInfraDeployed` twice; the shield prevents that once the first commit succeeds.

So: **Phase 4** is the Outbox Relay on the return path (VIM → Kafka). **Phase 5** is the Consumer ACK and Idempotent Shield on LCM (process once per message_id, ack only after commit).

---

## 5. How the Idempotent Receiver Prevents Duplicate Kubernetes Pods on Kafka Redelivery

### Why Duplicates Happen

- **At-least-once delivery**: Kafka (and the outbox relay) may deliver the same message more than once (e.g. consumer processes message, then crashes before committing offset; or relay sends to Kafka but crashes before marking outbox row as published).
- If we **do not** deduplicate, each redelivery could trigger **another** “create deployment” and **another** set of pods → **duplicate infrastructure**.

### Idempotent Receiver: “Process Each Logical Message Only Once”

We use two layers:

1. **Application-level check**  
   Before creating a `CloudDeployment`, we check:  
   `cloudDeploymentRepository.existsByLastProcessedEventId(eventId)`  
   If `true`, we **return without creating anything**. So duplicate messages are ignored.

2. **Database constraint**  
   We store the Kafka message id (or a unique event id) in `last_processed_event_id` and put a **UNIQUE** constraint on that column.  
   - First delivery: we insert a new `CloudDeployment` with that `eventId` → success.  
   - Redelivery: we try to insert again with the **same** `eventId` → **UNIQUE constraint violation**. We catch `DataIntegrityViolationException` and treat it as “already processed” (no duplicate pods, no rethrow).

The **constraint** is the strong guarantee: even with concurrent consumers or race conditions, only one row per `eventId` can exist. So we never create duplicate deployments (and thus duplicate pods) even if Kafka redelivers the same message.

---

## Summary Table

| Topic                         | Takeaway                                                                 |
|------------------------------|---------------------------------------------------------------------------|
| **Business flow**            | LCM creates `VnfInstance` + outbox → LCM relay → Kafka → VIM consumes, creates `CloudDeployment`, writes reply to VIM outbox → VIM relay → Kafka reply topic → LCM consumes, checks `processed_message`, calls `markInfraDeployed`, records message_id, then ack. |
| **Transactional Outbox**     | Business row + outbox row in same DB transaction → atomicity; relay sends to Kafka (both LCM and VIM use this pattern). Acceptable on Management Plane (low TPS, high consistency). |
| **@Version**                 | Optimistic locking; prevents lost updates (Sam and Mary); second commit fails with `OptimisticLockException`. |
| **Idempotent Receiver (VIM)** | Check `existsByLastProcessedEventId` + UNIQUE on `last_processed_event_id`; duplicate Kafka deliveries do not create duplicate pods. |
| **Return trip (Phase 3–5)**  | VIM writes `InfraDeployedReplyEvent` to outbox (Phase 3); VIM OutboxRelay publishes to `infra-deployed-reply` (Phase 4); LCM `LcmEventHandler` consumes, uses `ProcessedMessageRepository` as shield, calls `markInfraDeployed`, then ack (Phase 5). |

Use this document together with the code (e.g. `VnfProfile`, `VnfInstance`, `LcmService`, `InfraDeploymentRequestedEvent`, `OutboxRelay`, `CloudDeployment`, `VimEventHandler`, `InfraDeployedReplyEvent`, `LcmEventHandler`, `ProcessedMessageEntity`) to walk through DDD, aggregate boundaries, Transactional Outbox, and Idempotent Receiver (forward and return) in a VNFM context during the interview.
