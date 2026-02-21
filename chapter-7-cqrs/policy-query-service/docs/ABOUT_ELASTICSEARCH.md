# About Elasticsearch

A short guide to **what Elasticsearch is** and the **key concepts** you need as a developer.

---

## What is Elasticsearch?

**Elasticsearch** is a **distributed search and analytics engine** built on **Apache Lucene**. It stores data as **JSON documents** and lets you search and analyze them in near real time.

- **Search**: Full-text search (like Google inside your data), exact match, filters, and aggregations (counts, sums, histograms).
- **Analytics**: Run aggregations over large volumes of data (e.g. “count by region”, “average by day”).
- **Distributed**: Data is spread across nodes; it scales horizontally and can replicate for availability.

In practice, developers use it as:

- A **search engine** (product search, log search, app search).
- A **query-oriented read store** (e.g. CQRS read side, dashboards, reporting).
- A **log and metrics store** (often with Kibana for visualization).

It is **not** a replacement for a primary transactional database: it is **eventually consistent** and optimized for search/analytics, not for strict ACID transactions and complex joins.

---

## Key concepts (developer view)

### 1. Index

An **index** is the top-level container for your data (similar to a “database” or “table” in the RDBMS world, depending on how you use it).

- You **write documents** into an index.
- You **search** within one index, several indices, or an **index pattern** (e.g. `logs-*` for all indices whose name starts with `logs-`).
- Each index has a **mapping** (schema) that defines field names and types.
- Indices are usually **named** in lowercase (e.g. `policy-history-2025-02-18`, `products`).

**Typical use:** One index per “entity” or per “time bucket” (e.g. one index per day for time-series data).

---

### 2. Document

A **document** is the unit of data in Elasticsearch: a **JSON object** stored in an index.

- Each document has a unique **`_id`** within its index (you can provide it or let Elasticsearch generate it).
- A document has **fields** (key–value pairs): strings, numbers, dates, booleans, nested objects, arrays.
- Documents are **schema-flexible** by default (dynamic mapping), but in production you usually define a **mapping** so that field types are consistent and search behaves as expected.

**RDBMS analogy:** A document is like a **row**; the index is like a **table** (or a set of tables when you use index patterns).

---

### 3. Mapping

The **mapping** is the “schema” of an index: it defines **field names** and **data types**.

- **Why it matters:** The type of a field (e.g. `keyword` vs `text`, `date` vs `long`) determines how it is stored, indexed, and queried.
- **Keyword**: Exact value; not split into tokens. Good for IDs, categories, filters, sorting, aggregations.
- **Text**: Analyzed (tokenized) for full-text search. Good for free text (descriptions, comments).
- **Date**: Stored as a date; supports range queries and date math.
- **Numeric types**: `long`, `integer`, `short`, `float`, `double` – for range queries and aggregations.
- **Boolean**: `true` / `false`.

If you don’t define a mapping, Elasticsearch infers one (dynamic mapping); for predictable behavior, define the mapping explicitly (e.g. via Spring Data `@Field(type = FieldType.Keyword)` or index templates).

---

### 4. Inverted index (how search works)

Elasticsearch uses an **inverted index**: for each term (word or value), it stores a list of document ids that contain that term.

- **Full-text search**: Text is analyzed (tokenized, lowercased, etc.), then the inverted index is used to find documents containing the query terms.
- **Exact match / filters**: For `keyword` (and similar) fields, the value is stored as a single term; lookup is fast and exact.

This is why **keyword** (exact) and **text** (full-text) are different: they are indexed and queried in different ways.

---

### 5. Shards and replicas (high level)

- An index can be split into **shards** (pieces of the index). Shards let Elasticsearch distribute data and queries across nodes.
- **Replicas** are copies of a shard; they provide redundancy and can serve read traffic.

As a developer you often just **choose the number of shards (and replicas)** when creating an index; the cluster handles placement and routing. For small apps or single-node setups, default settings (e.g. 1 shard, 0 replicas) are common.

---

### 6. Write path (indexing)

- **Index (verb)**: Add or update a document in an index. You send the document (and optionally an `_id`). If the document id already exists, the document can be updated (depending on opType).
- **Bulk**: Send many index/delete/update operations in one request for better throughput.
- **Refresh**: By default, indexed documents become searchable after a short delay (near real time). You can trigger a refresh explicitly if you need immediate visibility.

---

### 7. Read path (search and get)

- **Get by ID**: Fetch a single document by `_id` from an index. Fast and direct (no query parsing).
- **Search**: Run a **query** (e.g. match, term, range, bool) over one or more indices. You get **hits** (matching documents), **total count**, and can add **aggregations**, **sorting**, and **pagination** (from/size or search_after).

**Query types (conceptually):**

- **Match** – full-text search (analyzed).
- **Term** – exact match on a keyword (or non-analyzed) field.
- **Range** – numeric or date ranges.
- **Bool** – combine queries with must / should / must_not / filter.

---

### 8. Richer full-text: analyzers, scoring, typo tolerance

When people say Elasticsearch offers **“richer full-text”** than a basic database, they usually mean these three things:

#### Analyzers

**What it is:** The process that turns text into **searchable tokens** when indexing and when the user types a query.

- **Simple (e.g. RDBMS LIKE):** “Blue Widgets” is one string; you search with `LIKE '%blue%'` for an exact substring.
- **Richer (Elasticsearch):** The same text is **analyzed** (lowercased, split into words, maybe stemmed). So “Blue Widgets” might become tokens like `blue`, `widget`. A user who searches **“widget”** can still match “Widgets”; **“BLUE”** matches “Blue” (case-normalized). You can add **synonyms** (e.g. “mobile” = “cell phone”), **stop words**, and language-specific rules.

So **analyzers** = how text is broken down and normalized so that search is smarter than a raw substring match.

#### Scoring (relevance)

**What it is:** Each hit gets a **relevance score** (“how well does this document match the query?”). Results are returned **sorted by that score** (best match first).

- **Simple (RDBMS):** `LIKE '%word%'` either matches or not; there’s no “how much.” Order is by date or id, not relevance.
- **Richer (Elasticsearch):** Both documents might contain the words, but one is ranked higher (e.g. term frequency, field length). You get a **ranked list** (best match at top), not just “all rows that contain the words.”

So **scoring** = ranking results by relevance to the query.

#### Typo tolerance (fuzzy)

**What it is:** The query still matches when the user **misspells** a word.

- **Simple (RDBMS):** “QuotaPolicy” matches; “QuotaPolisy” or “Quota Polic” does not (unless you add custom logic).
- **Richer (Elasticsearch):** A **fuzzy** query lets “QuotaPolisy” match “QuotaPolicy” (within 1–2 character edits). **Suggesters** can offer “QuotaPolicy” when the user types “Quota Polic.” Elasticsearch uses **edit distance** (e.g. Levenshtein) to tolerate small spelling mistakes.

So **typo tolerance** = still finding the right document when the user makes small spelling errors.

#### Other “richer” features

- **Phrase search:** Match an exact phrase in order (e.g. “policy denied”).
- **Boosting:** Weight some fields more (e.g. match in “title” scores higher than in “description”).
- **Highlighting:** Return snippets with the matched words **highlighted**.
- **Stemming:** “running” / “runs” / “run” treated as the same concept.

**Summary:** “Richer full-text” means Elasticsearch is built to do **analyzers**, **scoring**, **typo tolerance**, and related features out of the box. An RDBMS can do some of this (e.g. FULLTEXT, custom scoring in SQL), but it’s not the primary focus of the database.

---

### 9. When to use Elasticsearch (as a developer)

**Good fit:**

- Full-text search (e.g. product names, descriptions, logs).
- Filtering and faceted search (e.g. by category, date range, status).
- Time-series or event data with time-range queries.
- Dashboards and analytics (aggregations, histograms).
- CQRS read models where you need flexible, fast queries.

**Less suitable:**

- Primary system of record for highly transactional, ACID-critical data (use a relational or transactional DB).
- Complex joins and relationships (ES is document-oriented; denormalize or use another store for complex relational models).
- Strong consistency requirements (ES is eventually consistent).

#### Elasticsearch vs RDBMS for the same query pattern

The **same query pattern** (filter by subscriber, date range, policy name, optional text search) can be implemented in an **RDBMS** with a table, indexes, and `WHERE` clauses (and optionally `LIKE` or full-text extensions). So “can we use this query using RDBMS?” → **yes**.

The **difference** is where each technology fits best:

- **RDBMS:** One store for writes and reads; ACID, joins, familiar SQL. Good when data volume is moderate and you don’t need advanced search.
- **Elasticsearch:** Often used as a **CQRS read model** or **search index** fed by events. Better when you want **richer full-text** (analyzers, scoring, typo tolerance), horizontal scaling for read-heavy/search workloads, or a dedicated store optimized for search and analytics.

Choosing ES doesn’t mean the query is “impossible” in an RDBMS; it means you’re optimizing for search/scale/CQRS. See **ELASTICSEARCH_CONCEPTS.md** for how this project uses ES and when it makes sense vs RDBMS.

---

## Quick glossary

| Term | Meaning |
|------|--------|
| **Index** | Container for documents; has a name and a mapping. |
| **Document** | JSON object stored in an index; has `_id` and fields. |
| **Mapping** | Schema: field names and types (keyword, text, date, long, etc.). |
| **Keyword** | Field type: exact value; used for filters, sort, aggregations. |
| **Text** | Field type: analyzed for full-text search. |
| **Shard** | Horizontal slice of an index; enables distribution. |
| **Replica** | Copy of a shard; for redundancy and read scaling. |
| **Query** | Request to find documents (match, term, range, bool, etc.). |
| **Aggregation** | Compute metrics or buckets over matching documents (count, sum, avg, terms, date_histogram, etc.). |

---

## Further reading

- [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html) – official reference.
- [Spring Data Elasticsearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/) – using Elasticsearch from Spring (e.g. `ElasticsearchOperations`, `@Document`, `CriteriaQuery`).

For how this project uses Elasticsearch (daily indices, idempotency, CriteriaQuery), see **ELASTICSEARCH_CONCEPTS.md** in this folder.
