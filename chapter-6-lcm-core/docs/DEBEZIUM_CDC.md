# Debezium CDC – Concepts and Configuration

This document answers common questions about Debezium CDC in the LCM project: what the configuration means, what data is published, and how consumers use it.

---

## 1. What is CDC?

**CDC** = **Change Data Capture**.

It means capturing changes to data (inserts, updates, deletes) as they happen—usually from the database transaction log (WAL in PostgreSQL)—and streaming them as events (e.g. to Kafka). Other systems can react in near real time without polling the database.

---

## 2. Is `lcm.debezium` a built-in or custom property?

**User-defined (custom).**

- `lcm.debezium.*` is **not** a Spring Boot or Debezium built-in namespace.
- It is defined in this project:
  - **`DebeziumProperties`** uses `@ConfigurationProperties(prefix = "lcm.debezium")` and binds all `lcm.debezium.*` keys from `application.yml`.
  - **`DebeziumListener`** uses `@ConditionalOnProperty(name = "lcm.debezium.enabled", havingValue = "true")` to start the embedded engine only when CDC is enabled.

---

## 3. Connector class

```text
connector.class = io.debezium.connector.postgresql.PostgresConnector
```

**`PostgresConnector`** is the Debezium connector that reads change data from **PostgreSQL**:

1. Connects to PostgreSQL using the configured host, port, user, password, and database name.
2. Uses **logical decoding** (e.g. the `pgoutput` plugin) to read row-level changes (INSERT/UPDATE/DELETE) from the database transaction log (WAL).
3. Converts those changes into events (e.g. JSON) and hands them to the embedded engine, which invokes `handleChangeEvent` for each change.

So this class is the **PostgreSQL CDC implementation**: it turns table changes into the change events the `DebeziumListener` receives. It comes from the `debezium-connector-postgres` dependency.

---

## 4. Connector configuration properties

These are set in `DebeziumListener` when building the engine config:

| Property | Meaning |
|----------|---------|
| **table.include.list** | Comma-separated list of tables to capture. Only changes on these tables are streamed (e.g. `public.events,public.outbox`). All other tables are ignored. |
| **plugin.name** | Which **logical decoding plugin** PostgreSQL uses to export changes. **pgoutput** is the built-in plugin (PostgreSQL 10+); no extra install. It outputs changes in a format the Debezium connector understands. |
| **slot.name** | Name of the **replication slot** in PostgreSQL. The slot is the “cursor” that tracks how far the connector has read in the WAL. Using a fixed name (e.g. `lcm_cdc_slot`) lets the connector resume from the same position after restarts. Must be unique per connector. |
| **publication.name** | Name of the **publication** in PostgreSQL. A publication defines which tables are included in logical replication. The connector uses it (with `pgoutput`) to receive only the tables you care about. The database may need `CREATE PUBLICATION lcm_cdc_pub FOR TABLE events, outbox;` (or the connector may create it if the user has the right permissions). |
| **snapshot.mode** | When to take a **snapshot** of existing rows. **initial** = on first start, do a full snapshot of the included tables, then stream only new changes. So you get existing data once, then a continuous stream of inserts/updates/deletes. |

---

## 5. Publication and slot – PostgreSQL concepts

**Yes. Publication and slot are PostgreSQL concepts**, not Debezium-specific.

- **Replication slot**  
  A replication slot is a PostgreSQL feature: it represents a consumer of the write-ahead log (WAL). The server keeps WAL until that slot has consumed it, so the connector can resume from where it left off after a restart. The slot is created and managed by PostgreSQL; Debezium just uses it by name (e.g. `lcm_cdc_slot`).

- **Publication**  
  A publication is a PostgreSQL object that defines **which tables** are part of logical replication. You add tables to a publication (e.g. `CREATE PUBLICATION lcm_cdc_pub FOR TABLE events, outbox;`). Replication then streams only changes for those tables. Debezium uses this so it only receives changes for the tables it needs.

So: **slot** = “where am I in the log” (state in Postgres); **publication** = “which tables to replicate” (also in Postgres). Debezium uses both when it connects with the `pgoutput` plugin for logical replication.

---

## 6. What does the change event `value` contain?

In `DebeziumListener.handleChangeEvent`, **`value`** is `event.value()`: the **Debezium change event as a JSON string**. Because the engine is created with **`Json.class`**, Debezium serializes each change record to JSON.

So **`value`** is a single JSON string in the standard **Debezium envelope** format, for example:

```json
{
  "before": null,
  "after": {
    "id": 1,
    "event_id": "evt-uuid",
    "aggregate_id": "vnf-123",
    "aggregate_type": "VNF",
    "version": 1,
    "event_type": "VnfInstantiationStarted",
    "payload": "{\"vnfId\":\"vnf-1\"}",
    "event_timestamp": "2025-02-18T10:00:00Z"
  },
  "source": {
    "version": "2.5.0.Final",
    "connector": "postgresql",
    "name": "lcm-cdc-connector",
    "ts_ms": 1739876400000,
    "snapshot": "false",
    "db": "vnfm_db",
    "schema": "public",
    "table": "events"
  },
  "op": "c",
  "ts_ms": 1739876400123
}
```

- **source** – Connector metadata; **source.table** is what the code uses to choose the Kafka topic (`events` vs `outbox`).
- **after** – New row state (used for the message key and, for outbox, the `destination` topic).
- **before** – Previous row (null for inserts).
- **op** – Operation: `c` (create), `u` (update), `d` (delete).

The code parses this JSON to read `source.table` and `after.event_id` / `after.destination` / `after.message_id`, then publishes the **entire** `value` string to Kafka as the message value.

---

## 7. How does a consumer get the required data from outbox CDC messages?

The consumer receives the **full Debezium JSON envelope** as the Kafka message value. The “required data to take action” is inside that envelope.

**Steps for the consumer:**

1. Parse the message value as JSON.
2. Read the **`after`** object—that is the outbox row (new state after the change).
3. Use:
   - **`after.message_type`** – e.g. `"ReserveResources"` → which handler or action to run.
   - **`after.payload`** – JSON string of the actual command (e.g. ReserveResources body); parse it and use it for the action.
   - **`after.message_id`** – for idempotency / deduplication (process each message at most once).

**Example:** For a message on `vim.commands`, the value might look like:

```json
{
  "before": null,
  "after": {
    "id": 42,
    "message_id": "msg-uuid",
    "destination": "vim.commands",
    "message_type": "ReserveResources",
    "payload": "{\"vnfId\":\"vnf-1\",\"sagaId\":\"saga-123\",\"resources\":{\"cpu\":2,\"memory\":4}}",
    "status": "PENDING",
    ...
  },
  "source": { "table": "outbox", ... },
  "op": "c",
  "ts_ms": 1739876400123
}
```

The consumer would:

- Use **`after.message_type`** to route to the ReserveResources handler.
- Parse **`after.payload`** to get `vnfId`, `sagaId`, `resources` and perform the action.
- Use **`after.message_id`** to deduplicate (e.g. in a processed-messages store).

So the consumer gets the required data by reading **`after.message_type`**, **`after.payload`**, and **`after.message_id`** from the Debezium envelope.

---

## 8. Toggle: OutboxForwarder vs DebeziumListener

A single property **`lcm.publisher.mode`** controls which component publishes outbox (and events) to Kafka:

| Value | Active component | Behaviour |
|-------|------------------|-----------|
| **outbox-forwarder** (default) | **OutboxForwarder** | Scheduled job polls the outbox table, publishes via KafkaTemplate, marks rows SENT. |
| **debezium-cdc** | **DebeziumListener** | Embedded Debezium streams changes from `events` and `outbox` tables to Kafka. |

Only one should be active to avoid duplicate outbox messages. If the property is missing, **outbox-forwarder** is used (`matchIfMissing = true` on OutboxForwarder).

Example in `application.yml`:

```yaml
lcm:
  publisher:
    mode: debezium-cdc   # use CDC; set to outbox-forwarder for the scheduled forwarder
```

---

## 9. Configuration reference (application.yml)

| Property | Description |
|----------|-------------|
| **lcm.publisher.mode** | `outbox-forwarder` (default) or `debezium-cdc` – toggles OutboxForwarder vs DebeziumListener. |
| **lcm.debezium.enabled** | When using Debezium, if `true`, the embedded engine starts (used inside DebeziumListener). |
| **lcm.debezium.database.hostname** | PostgreSQL host. |
| **lcm.debezium.database.port** | PostgreSQL port. |
| **lcm.debezium.database.username** | Database user. |
| **lcm.debezium.database.password** | Database password. |
| **lcm.debezium.database.dbname** | Database name. |
| **lcm.debezium.table-include-list** | Comma-separated tables, e.g. `public.events,public.outbox`. |
| **lcm.debezium.events-topic** | Kafka topic for the **events** table (default: `vnf.events`). |
| **lcm.debezium.connector-name** | Connector name for offset storage and logging. |

Outbox rows are published to the topic given by the row’s **`destination`** column (e.g. `vim.commands`).

---

For the overall flow and architecture, see **FLOW.md** §11 (Debezium CDC).
