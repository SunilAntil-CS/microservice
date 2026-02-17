# Chapter 3 – Module 4: The Idempotent Consumer

**This repo:** Chapter 3 (Microservices). Notes and interview cheat sheet for **Module 4** — handling duplicate messages so we create **one** trouble ticket per logical event.

---

## The Problem

- **At-Least-Once delivery:** Kafka (and most brokers) guarantee you will not lose a message; they **do not** guarantee exactly-once. You can receive the same message twice.
- **Why duplicates:** (1) Consumer processes the message but ACK fails → broker redelivers. (2) Outbox relay crashes after sending to Kafka but before marking row published → same message sent again.
- **Risk:** If logic is "create ticket" or "deduct wallet", processing twice = duplicate ticket or double charge.

---

## Idempotency

- **Concept:** Performing the operation multiple times has the **same result** as once: `f(f(x)) = f(x)`.
- **Analogy:** Elevator button — press 50 times, one elevator comes. Not a "toggle" (press again = different result).

---

## Strategy: Deduplication Table

We **track message_id** in a table and only process if we have not seen it before.

1. Every message has a unique **message_id** (e.g. from producer/outbox).
2. Consumer has a table **received_messages** with `PRIMARY KEY (consumer_id, message_id)`.
3. In **one** DB transaction:
   - Try `INSERT INTO received_messages (consumer_id, message_id)`.
   - If **duplicate key** → we already processed this message → **return** (do nothing).
   - If **success** → run business logic (e.g. create ticket), then **commit**.

**Rule:** The idempotency check and the business update must be in the **same ACID transaction**. If you check Redis and then write to MySQL, a crash between the two can cause duplicates or lost updates. The DB primary key is the guard rail.

---

## Architecture (ACID Guard)

```
[ Transaction START ]
  |
  +-- 1. INSERT INTO received_messages (consumer_id, message_id)
  |      If duplicate key → ROLLBACK & return (already processed)
  |
  +-- 2. INSERT INTO trouble_ticket (...)
  |      Business logic
  |
[ COMMIT ]
```

---

## Production Gotchas

1. **Cache trap:** Do not use Redis (or any separate store) for "have I seen this id?". Use the **same database** and same transaction as the business write. Otherwise you have a race.
2. **Table bloat:** `received_messages` grows forever. Add a **cleanup job** (e.g. delete rows older than 7 days). Retries usually happen within minutes.
3. **Poison messages:** A bad message can crash the consumer every time → infinite retry loop. Use a **Dead Letter Queue (DLQ)**: after N failures, move the message to a separate topic for inspection.

---

## Project Layout (Chapter 3 – Module 4)

```
ch3-fms/
├── model/          LinkDownEvent, ReceivedMessage, ReceivedMessageId, TroubleTicket
├── repository/     ReceivedMessageRepository, TicketRepository
├── consumer/       AlarmEventConsumer  (@KafkaListener + @Transactional idempotency)
├── controller/     AlarmSimulateController (POST /simulate), TicketController (GET /tickets)
└── resources/sql/schema.sql   received_messages + trouble_ticket
```

---

## How to Run

1. Start Kafka (e.g. `localhost:9092`).
2. Start FMS: `cd ch3-fms && mvn spring-boot:run` (port 8083).
3. **Simulate** (same messageId twice to see idempotency):
   - `curl -X POST http://localhost:8083/api/v1/alarms/simulate -H "Content-Type: application/json" -d '{"nodeId":"node-1","linkId":"link-1","severity":"MAJOR","messageId":"test-msg-1"}'`
   - Repeat the **same** request (same `messageId`). Second time: no new ticket.
4. **List tickets:** `curl http://localhost:8083/api/v1/tickets` — you should see only one ticket for `test-msg-1`.

---

## Key Terms

- **At-Least-Once** — May receive duplicates; consumer must be idempotent.
- **Idempotency** — `f(x) = f(f(x))`; processing twice is safe.
- **Deduplication table** — `received_messages` with PK on (consumer_id, message_id).
- **Same transaction** — Idempotency insert and business insert in one ACID transaction.
- **DLQ** — Dead Letter Queue for poison messages.

---

## Interview Q&A

**Q: Why not check "have I seen this id?" in Redis?**  
Redis and DB are different systems. If the app crashes after checking Redis (key absent) and before writing to DB, on redelivery Redis is still empty → duplicate processing. Use one DB and one transaction.

**Q: How do you avoid received_messages growing forever?**  
Cleanup job: delete rows older than 7 days (or similar). Retries are usually within minutes; old rows are safe to remove.

**Q: What if the message is invalid and always throws?**  
Use a DLQ: after N retries, move the message to a separate topic and stop retrying so the consumer does not loop forever.
