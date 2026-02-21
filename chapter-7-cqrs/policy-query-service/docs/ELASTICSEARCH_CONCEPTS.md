# Elasticsearch Concepts Used in Policy Query Service

This document explains the Elasticsearch concepts and patterns used in this project: how we model data, write it, and query it.

---

## 1. Why Elasticsearch here?

The Policy Query Service is the **read side** of a CQRS setup. We need to:

- **Store** policy events (append-only, by time) and **query** them by subscriber, time range, policy name, with **pagination**.
- Support **filtering and search** over potentially large volumes of events.

**Elasticsearch** fits because it is a **search engine** built on Lucene: it indexes JSON documents and supports fast filtered and full-text search, aggregations, and pagination. We use it as a **query-optimized read store** (not as the system of record; events are published via Kafka from the write side).

---

## 2. Core concepts (as used in this project)

### 2.1 Index

An **index** is the top-level container for documents (like a “table” in a DB). Each document belongs to exactly one index.

- **In this project:** We use **daily indices** named by date, e.g. **`policy-history-2025-02-18`**, **`policy-history-2025-02-19`**.
- **Why daily?** Keeps each index to a manageable size, makes it easy to delete or archive old data by dropping an index, and aligns with time-range queries (e.g. “last 7 days” touches a small set of indices).
- The **index name prefix** is configurable: **`policy-query.elasticsearch.index-prefix`** (default: **policy-history**). The full name is **`{prefix}-yyyy-MM-dd`** (UTC).

### 2.2 Document

A **document** is a JSON object stored in an index. It has an **`_id`** (document id) and a set of **fields**.

- **In this project:** Each policy event is one document: **PolicyEventDocument**.
- **Document id:** We use the **event id** (UUID) as `_id`. That gives **idempotency**: the same event (same id) is not indexed twice in the same index.
- **Fields:** `id`, `subscriberId`, `timestamp`, `policyName`, `decision`, `quotaUsed`, **searchableText** (see §2.3).

### 2.3 Field types (mapping)

Elasticsearch stores fields with a **type** that affects how they are indexed and queried. We set them explicitly via **Spring Data Elasticsearch** `@Field(type = ...)`:

| Field          | Type      | Meaning |
|----------------|-----------|--------|
| **subscriberId** | Keyword   | Exact value; not analyzed. Good for IDs, filters, aggregations. |
| **timestamp**  | Date      | Stored as date; supports range queries (from/to). |
| **policyName** | Keyword   | Exact match; use for filters (e.g. “policy A only”). |
| **decision**   | Boolean   | true/false. |
| **quotaUsed**  | Long      | Numeric; for aggregations or range queries if needed. |
| **searchableText** | Text    | Combined text (subscriberId + policyName + “allowed”/“denied”) for free-text / natural-language-style search via the **search** query param. |

- **Keyword** = one token, exact match. We use it for **subscriberId** and **policyName** because we filter by exact value, not full-text search.
- **Date** = range queries (e.g. `timestamp >= from AND timestamp <= to`).
- The **id** field is the document `_id`; it is not stored as a separate field in the mapping unless you add it.

---

## 3. Write path: indexing a policy event

### 3.1 Flow

1. **PolicyEventIndexer.index(event)** receives a **PolicyEventPayload** (from Kafka).
2. **Index name:** **PolicyIndexNameProvider.indexName(event.timestamp())** returns the daily index for that event’s date (e.g. **policy-history-2025-02-18**).
3. **Document id:** **eventId** (UUID) is used as the Elasticsearch document id.
4. **Idempotency:** We **get** the document by id in that index. If it already exists, we **skip** indexing (duplicate event from Kafka replay or retry).
5. **Index:** We build an **IndexQuery** with that id and the **PolicyEventDocument** and call **ElasticsearchOperations.index(...)** for that index.
6. **Retry:** **@Retryable** retries on transient failures (e.g. Elasticsearch temporarily unavailable) with backoff.

### 3.2 Idempotency (why we check “exists” before index)

Kafka can deliver the same message more than once (at-least-once). If we indexed every time without checking, we would create duplicate documents for the same event. By using **eventId** as `_id` and **checking for an existing document** in that index before indexing, we only write once per event (per day). Same event processed twice → second time we skip.

### 3.3 Daily index at write time

The **date used for the index name is the event’s timestamp**, not “now”. So an event that happened on 2025-02-17 is stored in **policy-history-2025-02-17** even if we index it on 2025-02-18. That keeps queries by time range consistent.

---

## 4. Read path: querying policy history

### 4.1 Flow

1. **PolicyHistoryQueryService.query(...)** receives filters: **subscriberId**, **from**, **to**, **policyName**, **search** (optional), **page**, **size**.
2. **Criteria:** We build a **Criteria** object:
   - **subscriberId** → `Criteria.where("subscriberId").is(subscriberId)`
   - **from** → `Criteria.where("timestamp").greaterThanEqual(from)`
   - **to** → `Criteria.where("timestamp").lessThanEqual(to)`
   - **policyName** → `Criteria.where("policyName").is(policyName)`
   - **search** (if provided) → `Criteria.where("searchableText").matches(search)` (full-text match on the combined text field)
3. **Query:** **CriteriaQuery** with that criteria and **PageRequest.of(page, size)** for pagination.
4. **Search:** **ElasticsearchOperations.search(query, PolicyEventDocument.class, IndexCoordinates.of("policy-history-*"))**.
   - **policy-history-*** is an **index pattern**: Elasticsearch searches **all indices** whose name matches (all daily indices). So one query can span multiple days.
5. **Result:** We map **SearchHits** to a list of **PolicyEventDocument** and return **PolicyHistoryPage** (content, totalCount, page, size, hasNext).

### 4.2 Index pattern for reads

- **Write:** We target a **single** daily index (e.g. **policy-history-2025-02-18**).
- **Read:** We use the **pattern policy-history-*** so the search runs over **all** daily indices. That way “last 30 days” or “subscriber X, from/to” works without knowing which indices to query.

### 4.3 Pagination

- **page** and **size** are applied via **PageRequest.of(page, size)**.
- **totalCount** comes from **hits.getTotalHits()** so the API can return “total number of matching documents” and “hasNext” for the client.

### 4.4 Natural language and free-text search

We support an optional **`search`** query parameter for **free-text** (natural-language-style) input. How it works and what it does **not** do:

**What we support:**

- **`search`** is applied as a **match** query on the **searchableText** field (subscriberId + policyName + “allowed”/“denied”, indexed as **text**). So the user can type words that appear in that content, e.g. **“sub-001”**, **“QuotaPolicy”**, **“denied”**, or **“sub-001 denied”**, and get matching documents.
- For **“give me details of this subscriber id”**, the user must supply the **actual** subscriber id: use **GET /api/policy-history/{subscriberId}** or **GET /api/policy-history?search=sub-001** (with the real id). The API does **not** understand the sentence; it only matches **terms that appear in the indexed text**.

**What we do not support:**

- **Intent understanding:** We do not parse natural language to infer “this subscriber id” or “give me details.” There is no NLP that maps a full sentence to filters. Use the **path parameter** or **structured query params** (subscriberId, from, to, policyName) for that.
- **Full-sentence match:** A phrase like “Give me details of this subscriber id” will not match any document, because the indexed content does not contain those words (it contains actual ids and “allowed”/“denied”).

So: **natural language** here means “free-text search over the words we indexed,” not “understand any sentence and translate it to a query.”

---

### 4.5 Elasticsearch vs RDBMS for this read model

The **same query pattern** (filter by subscriberId, from/to, policyName, optional text search) can be implemented in an **RDBMS** with a single table, indexes, and SQL `WHERE` (and optionally `LIKE` or full-text). So we **could** use an RDBMS for this read side.

We use **Elasticsearch** in this project because:

- **CQRS:** The read model is fed by events (Kafka); ES is a common choice for a dedicated, query-optimized read store.
- **Richer full-text (optional):** If we need analyzers, relevance scoring, typo tolerance, or heavier full-text later, ES supports them out of the box (see **ABOUT_ELASTICSEARCH.md** §8).
- **Scale:** For very read-heavy or search-heavy workloads, ES is built to scale out (shards, replicas).

When an **RDBMS is enough:** moderate data volume, one store for writes and reads, no need for advanced search. Then a table + indexes (and optional FULLTEXT) is sufficient. The “difference” is not that the query is impossible in RDBMS; it’s that we chose a search-oriented store for the read side.

---

## 5. Components in this project

| Component | Role |
|-----------|------|
| **PolicyEventDocument** | Elasticsearch document model: id, subscriberId, timestamp, policyName, decision, quotaUsed, **searchableText** (for free-text search). **@Document(indexName = "policy-history")** – base name; actual index is chosen at write time. **@Field(type = ...)** defines mapping. |
| **PolicyIndexNameProvider** | Computes the daily index name: **{prefix}-yyyy-MM-dd** from config and the event (or current) timestamp. |
| **PolicyEventIndexer** | Writes one event to the correct daily index; idempotent by eventId; uses **ElasticsearchOperations** and **IndexQuery**. |
| **PolicyHistoryQueryService** | Builds **Criteria** + **CriteriaQuery**, runs **search** on **policy-history-***, returns **PolicyHistoryPage**. |
| **ElasticsearchOperations** | Spring Data Elasticsearch API used for **get**, **index**, and **search** (low-level control over index name / pattern). |

---

## 6. Configuration

| Config | Purpose |
|--------|---------|
| **spring.elasticsearch.uris** | Elasticsearch HTTP endpoint (e.g. http://localhost:9200). |
| **spring.data.elasticsearch** | Optional cluster name / nodes (for the transport client if used). |
| **policy-query.elasticsearch.index-prefix** | Prefix for daily indices (default: **policy-history**). |

Index names are **policy-history-yyyy-MM-dd**. No need to create indices manually if Elasticsearch auto-creates them on first index; otherwise ensure the index template or creation uses the same mapping as **PolicyEventDocument** (Keyword, Date, etc.).

---

## 7. Summary diagram

```
Write (Kafka → ES):
  PolicyEventPayload (eventId, subscriberId, timestamp, ...)
       → PolicyIndexNameProvider.indexName(timestamp)  →  "policy-history-2025-02-18"
       → PolicyEventDocument (id = eventId, ...)
       → Idempotency: get(docId) in that index; if exists → skip
       → ElasticsearchOperations.index(IndexQuery, IndexCoordinates.of(indexName))

Read (REST → ES):
  Filters (subscriberId, from, to, policyName, search, page, size)
       → Criteria (subscriberId, timestamp range, policyName, optional searchableText match)
       → CriteriaQuery + PageRequest
       → ElasticsearchOperations.search(query, PolicyEventDocument.class, "policy-history-*")
       → PolicyHistoryPage(content, totalCount, page, size, hasNext)
```

---

## 8. Concepts recap

| Concept | In this project |
|---------|------------------|
| **Index** | One per day: **policy-history-yyyy-MM-dd**. |
| **Document** | One policy event = one document; **PolicyEventDocument**; **_id** = eventId. |
| **Field types** | Keyword (subscriberId, policyName), Date (timestamp), Boolean (decision), Long (quotaUsed), Text (searchableText). |
| **Write** | Single index per document; idempotent by eventId; daily index from event timestamp; searchableText built from subscriberId + policyName + allowed/denied. |
| **Read** | Index pattern **policy-history-***; CriteriaQuery with filters + optional **search** (match on searchableText) + pagination. |
| **Idempotency** | Same eventId in same index → skip duplicate index. |
| **Pagination** | PageRequest + totalHits for page/size and hasNext. |

For end-to-end flow (Kafka → indexer → ES, and REST → query service → ES), see **PROJECT_FLOW.md**.
