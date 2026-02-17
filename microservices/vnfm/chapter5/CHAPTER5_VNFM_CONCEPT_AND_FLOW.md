# Chapter 5: VNFM Distributed Saga — Concept and Project Flow

This document explains the architecture, concepts, and end-to-end flow of the **Chapter 5** VNFM (Virtual Network Function Manager) project: a telecom-grade, event-driven system based on ETSI MANO, using **Pure Choreography** (no central orchestrator), **Transactional Outbox**, and **Idempotent Consumers**.

---

## 1. Why Choreography (Not Orchestration)?

We implement a **Distributed Saga** using **Pure Choreography** (event-driven) rather than **Orchestration** (e.g. Temporal, Camunda).

- **Orchestration**: A central engine (orchestrator) tells each service what to do and tracks the saga state. Good when many actors and complex flows; adds operational and cognitive cost.
- **Choreography**: Each service reacts to events and emits new events; there is no central coordinator. Services are loosely coupled; the “saga” is implicit in the event flow.

**For a strictly 2-actor system (LCM and VIM Adapter), bringing in a heavy Orchestrator is over-engineering.** Choreography provides perfect decoupling: the LCM does not know the VIM’s implementation; the VIM does not know who else might consume its events. The only contract is the event schema (in `vnfm-common`).

**The “Lost in Space” flaw of Choreography:** If the VIM crashes after consuming a request but before sending a reply, the LCM has no single place to ask “what’s the status?” — the instance could stay in `DEPLOYING_INFRA` forever. We mitigate this with a **Timeout Watchdog**: a `@Scheduled` job in the LCM that finds instances stuck in `DEPLOYING_INFRA` for more than 15 minutes and marks them `FAILED`, notifying the NFVO. So we get the benefits of Choreography without leaving instances “lost in space”.

---

## 2. Reliability Patterns

### 2.1 Transactional Outbox (Hard-Delete Relay)

- **Problem:** If we update the database and then send a message to Kafka in two steps, one can succeed and the other fail (dual-write). We might commit the DB and then fail to send to Kafka, or vice versa.
- **Solution:** We never call Kafka inside the business transaction. We only **write the event to an `outbox_messages` table** in the **same transaction** as the aggregate update. So either both the aggregate row and the outbox row are committed, or neither.
- A separate **Outbox Relay** (a `@Scheduled` job) polls the outbox, sends each payload to Kafka using **`kafkaTemplate.send(...).get(timeout)`** for synchronous broker ACKs, and then **hard-deletes** the row. We do **not** just set `published=1`, to avoid table bloat: only in-flight messages remain in the table.

### 2.2 Idempotent Consumer (processed_messages)

- **Problem:** Kafka gives at-least-once delivery. The same message can be redelivered. If we process it twice, we might mark a VNF active twice or run compensation twice.
- **Solution:** Before applying business logic, we try to **INSERT the message’s unique ID** (e.g. `message_id` from the outbox) into a **`processed_messages`** table. The table has a **primary key** on that ID. Only one consumer can insert a given ID; duplicates get a constraint violation. We catch it, treat the message as already processed, and **ack** without re-running logic. We use **manual ACK** and call **`Acknowledgment.acknowledge()` only after the transaction commits** (e.g. via `TransactionSynchronization.afterCommit()`), so we never advance the offset before our DB changes are durable.

---

## 3. Module Overview

| Module | Role |
|--------|------|
| **vnfm-common** | Shared domain events and DTOs (no Spring/JPA). Used by LCM, VIM Adapter, and NFVO simulator. |
| **vnfm-lcm-service** | “The Brain”: VnfInstance aggregate, outbox, relay, idempotent consumers for VIM events, Timeout Watchdog, VnfStatusUpdatedEvent to NFVO. |
| **vnfm-vim-adapter-service** | Anti-Corruption Layer: consumes InfraDeploymentRequestedEvent, writes ACK to outbox, calls VIM Simulator async; webhook receives Progress/Success/Failure and writes to outbox. |
| **simulator-vim** | OpenStack mock: POST /api/openstack/deploy → 202; async: Progress webhook, then Success or Failure (random). |
| **simulator-nfvo** | NFVO mock: Kafka listener on `nfvo-vnf-notifications`; logs “NFVO Dashboard Updated: VNF {id} is now {state}”. |

---

## 4. Event Flow (3-Event Lifecycle and NFVO)

### 4.1 LCM → VIM

- **InfraDeploymentRequestedEvent**  
  LCM creates a `VnfInstance` in `DEPLOYING_INFRA`, saves it and writes this event to the outbox (one transaction). The relay publishes to topic `infra-deployment-requested`.  
  LCM also publishes **VnfStatusUpdatedEvent** (state `DEPLOYING_INFRA`) to `nfvo-vnf-notifications`.

### 4.2 VIM → LCM (3-Event Lifecycle)

1. **InfraDeploymentAcceptedEvent** (ACK)  
   VIM Adapter consumes the request, creates a `CloudDeployment`, writes this event to its outbox, then calls the VIM Simulator asynchronously. Relay publishes to `infra-deployment-accepted`. LCM consumes (idempotent), typically just logs.

2. **InfraDeploymentProgressEvent** (Progress)  
   Simulator calls the VIM Adapter’s webhook with type `PROGRESS`. Adapter maps to this event and writes to outbox. Relay publishes to `infra-deployment-progress`. LCM consumes (idempotent), logs progress.

3. **InfraDeployedReplyEvent** (Terminal Success) or **InfraDeploymentFailedEvent** (Terminal Failure)  
   Simulator calls the webhook with `SUCCESS` or `FAILURE`. Adapter writes the corresponding event to outbox. Relay publishes to `infra-deployed-reply` or `infra-deployment-failed`.  
   - **Success:** LCM calls `markInfraDeployed(vnfId, deploymentId)` → state `ACTIVE`, publishes **VnfStatusUpdatedEvent**.  
   - **Failure:** LCM runs **compensation**: `markInfraFailed(vnfId, reason)` → state `FAILED`, publishes **VnfStatusUpdatedEvent** so the NFVO can react.

### 4.3 LCM → NFVO

- **VnfStatusUpdatedEvent**  
  Published by LCM whenever a VnfInstance’s state changes (e.g. `DEPLOYING_INFRA`, `ACTIVE`, `FAILED`). Destination topic: `nfvo-vnf-notifications`. The NFVO simulator (or real NFVO) consumes and updates the “dashboard” (here: log to console).

---

## 5. State Machine (VnfInstance)

- **INSTANTIATING** → **DEPLOYING_INFRA** → **ACTIVE** | **FAILED**
- `DEPLOYING_INFRA`: waiting for VIM’s ACK, Progress, and then Reply or Failed.
- **Timeout Watchdog:** Finds instances in `DEPLOYING_INFRA` with `updated_at` older than 15 minutes, marks them `FAILED` and publishes **VnfStatusUpdatedEvent**.

---

## 6. Outbox Relay and Idempotent Consumer (Recap)

- **Outbox Relay:** Runs on a schedule; reads unpublished rows from `outbox_messages`; for each row, sends payload to Kafka (topic = `destination`), using **`.get(timeout)`** for sync broker ACK; on success **deletes** the row (hard-delete strategy). This is why we use Choreography without a central engine: the DB + outbox is our source of truth; the relay is a simple, stateless bridge to Kafka.
- **Idempotent Consumer:** For every VIM-originated event (ACK, Progress, Reply, Failed), the LCM listener first tries to insert `message_id` into `processed_messages`. Duplicates are skipped and acked. Only after a successful insert do we run business logic (e.g. `markInfraDeployed` or `markInfraFailed`) and then ack in `afterCommit`. This gives at-least-once delivery with effectively once-only processing.

---

## 7. Project Flow Summary

1. Operator or API calls LCM **POST /api/v1/vnf/instantiate** with `vnfId` and profile.
2. LCM creates **VnfInstance** in **DEPLOYING_INFRA**, writes **InfraDeploymentRequestedEvent** and **VnfStatusUpdatedEvent** to outbox (one TX).
3. **Outbox Relay (LCM)** publishes to `infra-deployment-requested` and `nfvo-vnf-notifications`; deletes outbox rows.
4. **VIM Adapter** consumes request; claims `message_id` in `processed_messages`; creates **CloudDeployment**; writes **InfraDeploymentAcceptedEvent** to outbox; commits; then **async** calls Simulator **POST /api/openstack/deploy** (with `callbackUrl`).
5. **Simulator** returns 202; after 2s POSTs **Progress** to VIM **/api/vim/callback**; after 3s POSTs **Success** or **Failure**.
6. **VIM Callback** maps webhook body to **InfraDeploymentProgressEvent** / **InfraDeployedReplyEvent** / **InfraDeploymentFailedEvent** and writes to outbox.
7. **Outbox Relay (VIM)** publishes to `infra-deployment-progress`, `infra-deployed-reply`, or `infra-deployment-failed`.
8. **LCM** consumes (idempotent): on Reply (success) → **markInfraDeployed** and **VnfStatusUpdatedEvent (ACTIVE)**; on Failed → **markInfraFailed** (compensation) and **VnfStatusUpdatedEvent (FAILED)**.
9. **NFVO** consumes **VnfStatusUpdatedEvent** and logs: “NFVO Dashboard Updated: VNF {id} is now {state}”.
10. **Timeout Watchdog (LCM)** periodically marks instances stuck in **DEPLOYING_INFRA** for > 15 min as **FAILED** and publishes **VnfStatusUpdatedEvent**.

This is the full concept and project flow for Chapter 5 VNFM.
