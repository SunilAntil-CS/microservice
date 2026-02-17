# Chapter 5: VNFM Distributed Saga — Concept and Project Flow

This document explains the architecture, concepts, and end-to-end flow of the **Chapter 5** VNFM (Virtual Network Function Manager) project: a telecom-grade, event-driven system based on ETSI MANO, using **Pure Choreography** (no central orchestrator), **Transactional Outbox**, and **Idempotent Consumers**.

---

## 1. Why Choreography (Not Orchestration)?

We implement a **Distributed Saga** using **Pure Choreography** (event-driven) rather than **Orchestration** (e.g. Temporal, Camunda).

- **Orchestration**: A central engine (orchestrator) tells each service what to do and tracks the saga state. Good when many actors and complex flows; adds operational and cognitive cost.
- **Choreography**: Each service reacts to events and emits new events; there is no central coordinator. Services are loosely coupled; the "saga" is implicit in the event flow.

**For a strictly 2-actor system (LCM and VIM Adapter), bringing in a heavy Orchestrator is over-engineering.** Choreography provides perfect decoupling: the LCM does not know the VIM's implementation; the VIM does not know who else might consume its events. The only contract is the event schema (in `vnfm-common`).

**The "Lost in Space" flaw of Choreography:** If the VIM crashes after consuming a request but before sending a reply, the LCM has no single place to ask "what's the status?" — the instance could stay in `DEPLOYING_INFRA` forever. We mitigate this with a **Timeout Watchdog**: a `@Scheduled` job in the LCM that finds instances stuck in `DEPLOYING_INFRA` for more than 15 minutes and marks them `FAILED`, notifying the NFVO. So we get the benefits of Choreography without leaving instances "lost in space".

---

## 2. Reliability Patterns

### 2.1 Transactional Outbox (Hard-Delete Relay)


### Need of Transactional Outbox

The **Transactional Outbox** pattern exists because of the fundamental "mismatch" between two different types of distributed technologies: **Relational Databases (SQL)** and **Message Brokers (Kafka/RabbitMQ)**.

Here is the breakdown of why those specific reasons you mentioned force this pattern into existence:

### 1. The "Technology Mismatch" (No Shared Transaction)

As you noted, we are dealing with two different types of technology.

* **Database (JPA/Hibernate):** Supports ACID transactions (Atomic, Consistent, Isolated, Durable).
* **Message Broker (Kafka):** Supports high-throughput streaming, but it does **not** join a database transaction.

There is no "Global Transaction" (like the old XA/2PC protocols, which are too slow for telecom) that can wrap both a `vnfRepository.save()` and a `kafkaTemplate.send()` in one single lock. Because they speak different "languages," one can succeed while the other fails.

### 2. The "ACID" Problem in Distributed Systems

In a monolithic system, you have full **ACID** properties. In your VNFM, you are trying to maintain that same level of integrity across **sub-transactions**:

* **Sub-transaction A:** Update VNF status to `DEPLOYING_INFRA`.
* **Sub-transaction B:** Inform the VIM Adapter to start work.

If Sub-transaction A commits but Sub-transaction B fails (because Kafka is down or the network blips), your system enters an **Inconsistent State**. The database says the VNF is deploying, but no work is actually happening in the cloud. The "Outbox" fixes this by making Sub-transaction B **part of the database's ACID boundary.**

### 3. The Three "Dual-Write" Failure Scenarios

This pattern specifically guards against the three ways a "Dual-Write" (writing to two places) fails:

1. **DB Fails, Kafka Succeeds:** You send the message to the VIM, but the DB crashes and rolls back. **Result:** A "Ghost VM" is created in the cloud that your VNFM doesn't know exists.
2. **DB Succeeds, Kafka Fails:** You update the DB to `DEPLOYING`, but the Kafka send fails. **Result:** A "Stuck VNF" that sits in the DB forever but never actually deploys.
3. **App Crashes in the Middle:** You update the DB, but the server loses power before it can even try to send the Kafka message.

### Summary

Yes, the pattern exists purely because:

1. **Heterogeneous Tech:** Databases and Brokers don't share a transaction coordinator.
2. **Atomic Requirement:** We must ensure that the "Fact" (DB update) and the "Announcement" (Message) are inseparable.

By saving the message into the **Outbox Table**, you are effectively "tricking" the technology mismatch—you turn a message-sending operation into a simple database insert, allowing you to use the database's own ACID properties to guarantee that the message will *eventually* be sent.

### Defination 

The **Transactional Outbox Design Pattern** is a reliability pattern used in microservices to ensure data consistency between a service's **database** and an **external message broker** (like Kafka or RabbitMQ).

It solves the "Dual-Write" problem: the situation where you need to update a database and send a message at the same time, but one might succeed while the other fails.

---

## 1. The Core Definition

In this pattern, instead of sending a message directly to a broker during a business transaction, the service saves the message into a special **Outbox table** within its own local database.

Since the business data and the outbox message are saved in the **same database transaction**, they are "atomic"—either both are saved, or nothing is saved. A separate process (the **Relay**) then polls this table and publishes the messages to the broker.

---

## 2. How it Works (Step-by-Step)

1. **Start Transaction:** The service begins a local database transaction.
2. **Update Business State:** It updates its primary entities (e.g., changing a VNF status to `ACTIVE`).
3. **Insert into Outbox:** In the same transaction, it inserts a record into an `outbox_messages` table containing the event payload.
4. **Commit:** The database transaction is committed.
5. **Relay/Publisher:** A background process (like the `OutboxRelay.java` in your code) periodically reads the outbox table.
6. **Publish:** The Relay sends the message to Kafka and waits for an acknowledgment.
7. **Cleanup:** Once Kafka confirms receipt, the Relay deletes or marks the message as "published" in the outbox table.

---

## 3. Why it is Critical for your VNFM

In a telecom environment like your project, the Outbox pattern is the "safety net" for your Saga:

* **Guaranteed Delivery:** If your LCM service crashes after updating the database but before sending a Kafka message, the event isn't lost. It stays in the Outbox table and will be sent as soon as the service restarts.
* **No "Ghost" States:** It prevents the NFVO from being out of sync. Without this, you might update a VNF to `FAILED` in your DB, but if the network fails before the event reaches the NFVO, the dashboard would incorrectly show it as `ACTIVE`.
* **Performance:** The main business thread doesn't have to wait for the message broker (which might be slow or down). It just writes to the local DB and moves on.

---

## 4. Example from your Code

In your `vnfm-lcm-service`, the pattern is implemented exactly like this:

> **The Writer:** `LcmService.java` uses `@Transactional` to save the `VnfInstance` and the `OutboxMessageEntity` together.
> **The Relay:** `OutboxRelay.java` runs a `@Scheduled` task that finds unpublished messages, calls `kafkaTemplate.send()`, and then **Hard Deletes** the row to keep the database lean.

Would you like me to show you the specific Java code blocks from your `vnfm-lcm-service` where this atomic "Double Write" happens?

### 2.2 Idempotent Consumer (processed_messages)



The **Idempotent Pattern** is a design principle in distributed systems ensuring that an operation can be performed multiple times without changing the result beyond the initial application.

In the context of microservices and message brokers like Kafka, it is the "safety shield" that prevents a system from processing the same message twice, even if that message is delivered multiple times.

---

### 1. The Core Definition

An operation is **idempotent** if:

> `f(x) = f(f(x))`

In plain English: If you send a command to "Create VNF #101" five times, the system should result in exactly **one** VNF being created, and the subsequent four requests should be safely ignored or acknowledged as already done.

---

### 2. Why it is Mandatory for your VNFM

In your project, you are using Kafka for communication. Kafka generally guarantees **At-Least-Once Delivery**. This means:

1. The VIM Adapter might process a request and send an ACK.
2. A network "hiccup" occurs, and Kafka doesn't receive the ACK.
3. Kafka assumes the message was lost and **redelivers** it.
4. Without the Idempotent Pattern, your VIM Adapter would try to create the **same VM a second time**, leading to resource waste and inconsistent data.

---

### 3. How it Works: The "Bouncer" Strategy

The most common way to implement this (and the way it is implemented in your code) is using a **Unique Correlation ID** and a **Processed Messages Table**.

1. **Unique ID:** Every event is assigned a unique `eventId` (usually a UUID) at the source (e.g., in the LCM Service).
2. **The Lock:** Before the consumer (VIM Adapter) performs any business logic, it attempts to insert this `eventId` into a database table (e.g., `processed_messages`).
3. **The Decision:**
* **If Insert Succeeds:** This is the first time we've seen this message. Proceed with the business logic.
* **If Insert Fails (Duplicate Key):** We have already processed this. **Stop immediately** and just acknowledge the message to Kafka so it doesn't send it again.



---

### 4. Real-World Implementation (From your Code)

In your `VimEventHandler.java`, you have implemented this pattern perfectly:

```java
@Transactional
public void handleDeploymentRequest(InfraDeploymentRequestedEvent event, Acknowledgment ack) {
    String eventId = event.getEventId();
    
    try {
        // The "Bouncer" check: Attempting to claim this message ID
        processedMessageRepository.saveAndFlush(new ProcessedMessageEntity(eventId));
    } catch (DataIntegrityViolationException e) {
        // If we catch this, it means the ID already exists in the DB
        log.info("Duplicate message detected: {}. Skipping.", eventId);
        safeAcknowledge(ack); // Acknowledge so Kafka stops retrying
        return; 
    }

    // Only if we reach here do we actually talk to the Cloud/Simulator
    cloudDeploymentService.deploy(event);
    safeAcknowledge(ack);
}

```

---

### 5. Benefits

* **Consistency:** Prevents "double-spending" of resources (e.g., creating two VMs when only one was paid for).
* **Fault Tolerance:** Allows you to aggressively retry failed operations without fear of corrupting data.
* **Simplified Logic:** The business logic doesn't need to check for duplicates; the "Idempotency Shield" handles it at the entry point.

**Summary:** While the **Outbox Pattern** ensures the message is *sent* at least once, the **Idempotent Pattern** ensures the message is *acted upon* exactly once. Together, they create the "Exactly-Once" processing semantics required for high-availability telecom systems.

- **Problem:** Kafka gives at-least-once delivery. The same message can be redelivered. If we process it twice, we might mark a VNF active twice or run compensation twice.
- **Solution:** Before applying business logic, we try to **INSERT the message's unique ID** (e.g. `message_id` from the outbox) into a **`processed_messages`** table. The table has a **primary key** on that ID. Only one consumer can insert a given ID; duplicates get a constraint violation. We catch it, treat the message as already processed, and **ack** without re-running logic. We use **manual ACK** and call **`Acknowledgment.acknowledge()` only after the transaction commits** (e.g. via `TransactionSynchronization.afterCommit()`), so we never advance the offset before our DB changes are durable.

---

## 3. Module Overview

| Module | Role |
|--------|------|
| **vnfm-common** | Shared domain events and DTOs (no Spring/JPA). Used by LCM, VIM Adapter, and NFVO simulator. |
| **vnfm-lcm-service** | "The Brain": VnfInstance aggregate, outbox, relay, idempotent consumers for VIM events, Timeout Watchdog, VnfStatusUpdatedEvent to NFVO. |
| **vnfm-vim-adapter-service** | Anti-Corruption Layer: consumes InfraDeploymentRequestedEvent, writes ACK to outbox, calls VIM Simulator async; webhook receives Progress/Success/Failure and writes to outbox. |
| **simulator-vim** | OpenStack mock: POST /api/openstack/deploy → 202; async: Progress webhook, then Success or Failure (random). |
| **simulator-nfvo** | NFVO mock: Kafka listener on `nfvo-vnf-notifications`; logs "NFVO Dashboard Updated: VNF {id} is now {state}". |

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
   Simulator calls the VIM Adapter's webhook with type `PROGRESS`. Adapter maps to this event and writes to outbox. Relay publishes to `infra-deployment-progress`. LCM consumes (idempotent), logs progress.

3. **InfraDeployedReplyEvent** (Terminal Success) or **InfraDeploymentFailedEvent** (Terminal Failure)  
   Simulator calls the webhook with `SUCCESS` or `FAILURE`. Adapter writes the corresponding event to outbox. Relay publishes to `infra-deployed-reply` or `infra-deployment-failed`.  
   - **Success:** LCM calls `markInfraDeployed(vnfId, deploymentId)` → state `ACTIVE`, publishes **VnfStatusUpdatedEvent**.  
   - **Failure:** LCM runs **compensation**: `markInfraFailed(vnfId, reason)` → state `FAILED`, publishes **VnfStatusUpdatedEvent** so the NFVO can react.

### 4.3 LCM → NFVO

- **VnfStatusUpdatedEvent**  
  Published by LCM whenever a VnfInstance's state changes (e.g. `DEPLOYING_INFRA`, `ACTIVE`, `FAILED`). Destination topic: `nfvo-vnf-notifications`. The NFVO simulator (or real NFVO) consumes and updates the "dashboard" (here: log to console).

---

## 5. State Machine (VnfInstance)

- **INSTANTIATING** → **DEPLOYING_INFRA** → **ACTIVE** | **FAILED**
- `DEPLOYING_INFRA`: waiting for VIM's ACK, Progress, and then Reply or Failed.
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
9. **NFVO** consumes **VnfStatusUpdatedEvent** and logs: "NFVO Dashboard Updated: VNF {id} is now {state}".
10. **Timeout Watchdog (LCM)** periodically marks instances stuck in **DEPLOYING_INFRA** for > 15 min as **FAILED** and publishes **VnfStatusUpdatedEvent**.

This is the full concept and project flow for Chapter 5 VNFM.
