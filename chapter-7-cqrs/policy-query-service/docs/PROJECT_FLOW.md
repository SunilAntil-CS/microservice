# Policy Query Service – Project Flow

This document describes how the **Policy Query Service** works: where data comes from, how it is stored, and how it is queried.

---

## 1. Role in the system

The Policy Query Service is the **read side** of a CQRS-style setup:

- **Write side** (elsewhere): Policy decisions or events are written to a database (or outbox). Those changes are published to Kafka (e.g. via Debezium CDC or an outbox forwarder) on the **policy-events** topic.
- **This service**: Consumes **policy-events** from Kafka, indexes them in **Elasticsearch**, and exposes a **REST API** to query policy history (by subscriber, time range, policy name, with pagination).

So: **Kafka → this service → Elasticsearch** for ingestion, and **REST → this service → Elasticsearch** for queries.

---

## 2. High-level flow

```
┌─────────────────┐     policy-events      ┌──────────────────────┐     index     ┌─────────────────┐
│  Write side /   │ ──────────────────────► │  Policy Query        │ ─────────────► │  Elasticsearch  │
│  Debezium CDC   │        (Kafka)          │  Service             │                │  policy-history-*│
└─────────────────┘                        └──────────────────────┘                └────────┬────────┘
                                        │                                              │
                                        │  GET /api/policy-history?...                 │ search
                                        │  GET /api/policy-history/{subscriberId}?...  │
                                        ◄─────────────────────────────────────────────┘
                                        │
                                        ▼
                                 REST clients (e.g. UI, APIs)
```

---

## 3. Ingest flow (Kafka → Elasticsearch)

### 3.1 Components

| Component | Package | Responsibility |
|-----------|---------|-----------------|
| **PolicyEventConsumer** | `infrastructure.kafka` | Listens to the **policy-events** Kafka topic, deserializes JSON to `PolicyEventPayload`, calls the indexer, handles failures and DLQ. |
| **PolicyEventIndexer** | `application` | Writes one event to Elasticsearch (daily index), with idempotency and retry. |
| **PolicyIndexNameProvider** | `infrastructure.elasticsearch` | Resolves the index name (e.g. **policy-history-2025-02-18**) from the event timestamp and config. |
| **PolicyEventDocument** | `infrastructure.elasticsearch` | Elasticsearch document model (id, subscriberId, timestamp, policyName, decision, quotaUsed). |

### 3.2 Step-by-step

1. **Kafka message** arrives on topic `policy-query.kafka.topic` (default: **policy-events**). The message value is JSON; the key is typically the event id (used for idempotency and logging).

2. **PolicyEventConsumer.onPolicyEvent**
   - Deserializes the value to **PolicyEventPayload** (eventId, subscriberId, timestamp, policyName, decision, quotaUsed).
   - Calls **PolicyEventIndexer.index(event)**.

3. **PolicyEventIndexer.index**
   - Gets the **daily index name** from **PolicyIndexNameProvider** (e.g. `policy-history-2025-02-18`).
   - Uses **eventId** as the Elasticsearch document id.
   - **Idempotency**: If a document with that id already exists in that index, the method returns without indexing again (duplicate event is skipped).
   - Builds a **PolicyEventDocument** and indexes it with **ElasticsearchOperations** (opType CREATE semantics).
   - On **transient failure** (e.g. Elasticsearch temporarily unavailable), **@Retryable** retries up to 3 times with backoff. If all retries fail, the exception propagates.

4. **Consumer error handling**
   - If **indexing fails** (after retries) or **deserialization fails**: the consumer sends the **original record** (key + value) to the **DLQ topic** (`policy-query.kafka.dlq-topic`, default: **policy-events-dlq**), then **acknowledges** the Kafka record so it is not reprocessed indefinitely.
   - **Manual ack** is used (`Acknowledgment.acknowledge()`), so offsets are committed only after successful processing or after sending to DLQ.

### 3.3 Configuration (ingest)

- **Kafka**: `policy-query.kafka.topic`, `policy-query.kafka.dlq-topic` (see `PolicyQueryProperties`).
- **Elasticsearch**: `policy-query.elasticsearch.index-prefix` (default **policy-history**); index name = `{prefix}-yyyy-MM-dd`.
- **Retry**: `@Retryable` on `PolicyEventIndexer.index` (maxAttempts = 3, backoff delay 500ms, multiplier 2). Optional Resilience4j config in `application.yml` for `indexPolicyEvent` if used elsewhere.

---

## 4. Query flow (REST → Elasticsearch)

### 4.1 Components

| Component | Package | Responsibility |
|-----------|---------|-----------------|
| **PolicyHistoryController** | `interfaces` | REST API: GET /api/policy-history and GET /api/policy-history/{subscriberId} with query params. |
| **PolicyHistoryQueryService** | `application` | Builds Elasticsearch criteria from filters, runs search, returns a page of results. |
| **PolicyHistoryPage** | `interfaces` | DTO: list of documents, total count, page, size, hasNext. |

### 4.2 API

- **GET /api/policy-history**
  - Query params: `from`, `to` (ISO-8601), `policyName`, `subscriberId`, `page` (default 0), `size` (default from `policy-query.pagination.default-size`, max from `policy-query.pagination.max-size`).
  - Returns **PolicyHistoryPage** (content = list of **PolicyEventDocument**, totalCount, page, size, hasNext).

- **GET /api/policy-history/{subscriberId}**
  - Same query params (from, to, policyName, page, size); **subscriberId** is from the path.
  - Returns **PolicyHistoryPage** for that subscriber.

### 4.3 Step-by-step

1. Client calls **GET /api/policy-history?...** or **GET /api/policy-history/{subscriberId}?...**.

2. **PolicyHistoryController** validates/defaults `page` and `size` using **PolicyQueryProperties** (pagination.defaultSize, pagination.maxSize), then calls **PolicyHistoryQueryService.query(subscriberId, from, to, policyName, page, size)**.

3. **PolicyHistoryQueryService.query**
   - Builds a **Criteria** from optional filters: subscriberId, timestamp range (from/to), policyName.
   - Builds a **CriteriaQuery** with **PageRequest.of(page, size)**.
   - Runs **elasticsearchOperations.search(...)** against the index pattern **policy-history-*** (all daily indices).
   - Maps **SearchHits** to a list of **PolicyEventDocument** and returns **PolicyHistoryPage(content, totalCount, page, size, hasNext)**.

---

## 5. DLQ flow

- **DlqListener** (infrastructure.kafka) listens to **policy-events-dlq** with group id `policy-query-service-dlq`.
- On each message it **logs a warning** (and in production you can add alerting or further processing).
- Messages land here when the main consumer **failed to index or deserialize** and sent the original record to the DLQ before acknowledging.

---

## 6. Package layout (summary)

| Layer / package | Main types |
|-----------------|------------|
| **interfaces** | PolicyHistoryController, PolicyHistoryPage |
| **application** | PolicyEventIndexer, PolicyHistoryQueryService |
| **domain** | PolicyEventPayload (Kafka payload DTO) |
| **infrastructure.kafka** | PolicyEventConsumer, DlqListener |
| **infrastructure.elasticsearch** | PolicyEventDocument, PolicyIndexNameProvider |
| **config** | PolicyQueryProperties |

---

## 7. Configuration reference

| Key | Purpose |
|-----|---------|
| **policy-query.kafka.topic** | Kafka topic to consume (default: policy-events). |
| **policy-query.kafka.dlq-topic** | Dead-letter topic (default: policy-events-dlq). |
| **policy-query.elasticsearch.index-prefix** | Index name prefix (default: policy-history). |
| **policy-query.pagination.default-size** | Default page size for API (default: 20). |
| **policy-query.pagination.max-size** | Max page size (default: 100). |

Kafka and Elasticsearch connection settings are under **spring.kafka** and **spring.data.elasticsearch** / **spring.elasticsearch** in **application.yml**.

---

## 8. End-to-end summary

1. **Upstream** publishes policy events to Kafka topic **policy-events** (e.g. from Debezium CDC or outbox).
2. **PolicyEventConsumer** consumes messages, deserializes to **PolicyEventPayload**, and calls **PolicyEventIndexer**.
3. **PolicyEventIndexer** writes to a **daily Elasticsearch index** (policy-history-yyyy-MM-dd) with **eventId** as document id (idempotent). Retries on transient failures; on permanent failure the consumer sends to **policy-events-dlq** and acks.
4. **DlqListener** consumes DLQ messages for logging/alerting.
5. **REST API** (PolicyHistoryController + PolicyHistoryQueryService) queries **policy-history-*** with filters and pagination and returns **PolicyHistoryPage**.

This gives a clear **CQRS read model**: events flow in via Kafka, are stored in Elasticsearch, and are queried via the REST API.
