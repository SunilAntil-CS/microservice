# Chapter 3 – Module 3: The Transactional Outbox Pattern

**This repo:** Chapter 3 (Microservices). This file is the notes and interview cheat sheet for **Module 3**. It summarises the pattern, the code in this project, and the Q&A you need for interviews.

---

## The Telecom Scenario

You are building the **Mediation Service**.

- **Job:** Parse raw Call Detail Records (CDRs) from a Switch/Tower.
- **Step 1:** Save the parsed CDR to the Database (for legal audit).
- **Step 2:** Send an event to the **Billing System** so the customer gets charged.
- **The Risk:** If you save to DB but fail to send the event (e.g. network down), the customer is **never billed**. This is called **Revenue Leakage**.

We implement the **Transactional Outbox** so that *every* saved CDR reliably produces a Billing Event.

---

## The "Illegal" Code (What Juniors Do)

Do not write this in production:

```java
@Transactional
public void processCdr(RawCdr rawCdr) {
    CdrEntity cdr = cdrRepository.save(parse(rawCdr));
    kafkaTemplate.send("billing-events", cdr.toJson());  // DANGER
}
```

**Why it's wrong:** Database and Kafka are two different systems. They do not share a transaction (Dual Write Problem). You can get Revenue Leakage (DB commits, Kafka fails) or Phantom Charge (Kafka sends, DB fails).

---

## The Production Approach: Outbox Pattern

1. In the **same** `@Transactional` method: save the CDR **and** insert a row into an **outbox** table (`message`) with the event payload.
2. **One DB transaction** — both inserts commit or both roll back. **Atomicity**.
3. A **Relay** (background job or CDC) reads from the outbox and publishes to Kafka.

In this project: `CdrMediationService.processCdr()` does `cdrRepository.save(cdr)` and `outboxMessageRepository.save(outbox)`. `OutboxRelay` runs on a schedule, selects `WHERE published = 0`, sends payload to Kafka, then marks `published = 1`.

---

## Schema

Outbox table (see `ch3-mediation/src/main/resources/sql/schema.sql`):

```sql
CREATE TABLE message (
  id VARCHAR(767) NOT NULL PRIMARY KEY,
  destination VARCHAR(1000) NOT NULL,
  headers VARCHAR(1000),
  payload CLOB NOT NULL,
  published SMALLINT DEFAULT 0,
  creation_time BIGINT
);
```

---

## Key Terms

- **Dual Write Problem** — Reason we avoid DB + Kafka in one logical step without one transaction.
- **Atomicity** — CDR and "intent to send event" saved together in one DB transaction.
- **Transactional Outbox** — Write event to a table in same transaction; separate process sends to broker.
- **CDC (Change Data Capture)** — e.g. Debezium reads DB log and pushes to Kafka.
- **Revenue Leakage** — CDR stored but billing event never sent.
- **At-Least-Once** — Relay may publish same message twice → **consumers must be idempotent** (Module 4).

---

## Interview Q&A (Foundations)

**Q1: Why a separate outbox table?**  
Overwrite problem (miss state changes), coupling (relay would need every table schema), performance (no poll on huge business tables).

**Q2: Why not @Transactional around DB save + Kafka send?**  
DB and Kafka don't share a transaction. Dual Write Problem: either Revenue Leakage or Phantom Charge. Outbox = same DB = atomicity.

---

## Senior Architect Questions

**Q3: Outbox guarantees At-Least-Once. What for consumers?**  
Kafka can have duplicates. Consumers (e.g. Billing) must be **idempotent** (track message/event IDs; Module 4).

**Q4: Polling vs Log Tailing?**  
Polling: `SELECT WHERE published=0` periodically; simpler. CDC (e.g. Debezium): read DB log; lower latency, no polling load.

**Q5: 50,000 CDRs/sec — isn't outbox insert a bottleneck?**  
Batching (many rows per transaction), partitioning (cleanup without full lock), or dedicated outbox store for extreme scale.

---

## Project Layout (Chapter 3 – Module 3)

```
ch3-mediation/
├── model/       RawCdr, CdrEntity, CdrProcessedEvent, OutboxMessage
├── repository/  CdrRepository, OutboxMessageRepository
├── service/     CdrMediationService  (@Transactional: save CDR + outbox)
├── relay/       OutboxRelay          (@Scheduled: poll outbox → Kafka)
├── controller/  CdrController        (POST /api/v1/cdr)
└── resources/sql/schema.sql
```

---

## How to Run

- **Without Kafka:** `cd ch3-mediation && mvn spring-boot:run`. CDR + outbox saved; relay logs if Kafka unavailable.
- **With Kafka:** Start Kafka, then:
  - `curl -X POST http://localhost:8082/api/v1/cdr -H "Content-Type: application/json" -d '{"callId":"c1","subscriberId":"s1","durationSeconds":60,"cellId":"cell-1"}'`
  - Check topic `billing-events`.

**Next:** Chapter 3 – Module 4 — The Idempotent Consumer.
