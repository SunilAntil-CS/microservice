# Chapter 7 – CQRS Policy Decision History

Production-grade **CQRS** system for policy decision history: command side (mock policy engine + outbox), **Debezium** to Kafka, query side (Kafka → Elasticsearch, REST API), with observability and DLQ.

## Architecture (ASCII)

```
                    ┌─────────────────────────────────────────────────────────────────┐
                    │                     INFRASTRUCTURE (Docker)                       │
                    │  PostgreSQL  │  Zookeeper  │  Kafka  │  Elasticsearch  │ Debezium │
                    └──────┬───────┴──────┬──────┴────┬────┴────────┬────────┴────┬─────┘
                           │              │           │             │              │
    COMMAND SIDE            │              │           │             │              │
    ┌──────────────────────┴──────────────┐            │             │              │
    │  policy-engine (Spring Boot)        │            │             │              │
    │  @Scheduled → PolicyEvent           │            │             │              │
    │  → outbox table (same transaction)  │────────────┼─────────────┼──────────────┤
    └────────────────────────────────────┘            │             │   CDC        │
                                                       │             │   (outbox)   │
                                                       ▼             │              │
                                              topic: policy-events   │              │
                                                       │             │              │
    QUERY SIDE                                          │             │              │
    ┌──────────────────────────────────────────────────┴─────────────┴──────────────┘
    │  policy-query-service (Spring Boot)
    │  @KafkaListener → idempotent index (ES) → manual ack
    │  On failure → DLQ (policy-events-dlq)
    │  GET /api/policy-history/{subscriberId}?from=&to=&policyName=&page=&size=
    └─────────────────────────────────────────────────────────────────────────────────
```

## Prerequisites

- Docker and Docker Compose
- Java 17, Maven 3.8+

## 1. Run infrastructure

```bash
cd chapter-7-cqrs
docker compose up -d
```

Wait until all services are healthy (PostgreSQL, Kafka, Elasticsearch, Debezium Connect). Check:

```bash
docker compose ps
curl -s http://localhost:8083/  # Debezium Connect
curl -s http://localhost:9200/_cluster/health  # Elasticsearch
```

## 2. Create Debezium publication (PostgreSQL)

The `init.sql` already creates the `outbox` table and publication `dbz_publication`. If you created the DB before adding the publication, run manually:

```bash
docker exec -it cqrs-postgres psql -U cqrs -d cqrs_db -c "CREATE PUBLICATION dbz_publication FOR TABLE outbox;"
```

## 3. Register Debezium connector

```bash
chmod +x scripts/register-debezium-connector.sh
./scripts/register-debezium-connector.sh http://localhost:8083
```

Or with curl (from project root):

```bash
curl -s -X POST -H "Content-Type: application/json" \
  --data @debezium-connector.json \
  http://localhost:8083/connectors
```

Check connector status:

```bash
curl -s http://localhost:8083/connectors/policy-outbox-connector/status | jq
```

## 4. Build and run applications

### Command side (policy engine)

```bash
cd policy-engine
mvn spring-boot:run
```

This starts the mock policy engine: every few seconds it generates a random `PolicyEvent` and writes it to the `outbox` table. Debezium streams these to the `policy-events` Kafka topic.

### Query side (policy query service)

```bash
cd policy-query-service
mvn spring-boot:run
```

Ensure Kafka bootstrap is reachable. If Docker Kafka is on `localhost:29092`, use:

```bash
mvn spring-boot:run -Dspring.kafka.bootstrap-servers=localhost:29092 -Dspring.elasticsearch.uris=http://localhost:9200
```

The service consumes from `policy-events`, indexes in Elasticsearch (daily index `policy-history-yyyy-MM-dd`), and exposes the REST API.

## 5. Test the REST API

After the policy engine has run for a short while and the query service has consumed some events:

```bash
# List all (with optional filters)
curl -s "http://localhost:8080/api/policy-history?page=0&size=10" | jq

# By subscriber
curl -s "http://localhost:8080/api/policy-history/sub-001?page=0&size=5" | jq

# With date range (ISO-8601)
curl -s "http://localhost:8080/api/policy-history/sub-001?from=2025-02-01T00:00:00Z&to=2025-02-19T23:59:59Z" | jq

# Filter by policy name
curl -s "http://localhost:8080/api/policy-history/sub-002?policyName=QuotaPolicy" | jq
```

## 6. Observability

- **Prometheus metrics:** `http://localhost:8080/actuator/prometheus`
- **Health:** `http://localhost:8080/actuator/health`
- **Info:** `http://localhost:8080/actuator/info`

To enable **Zipkin** tracing, run Zipkin (e.g. Docker) and set:

```yaml
management.tracing.sampling.probability: 1.0
zipkin.base-url: http://localhost:9411
```

Structured logging (MDC: `eventId`, `subscriberId`, `traceId`) is configured in `policy-query-service` with logback.

## 7. Dead letter queue

Failed events (e.g. after retries) are sent to `policy-events-dlq`. The query service has a `DlqListener` that logs DLQ messages. In production you can add alerting or a separate DLQ consumer.

## 8. Project layout

```
chapter-7-cqrs/
├── docker-compose.yml       # PostgreSQL, Kafka, Zookeeper, ES, Debezium
├── init.sql                 # outbox table + publication
├── debezium-connector.json  # Connector config (outbox, DLQ)
├── scripts/
│   └── register-debezium-connector.sh
├── policy-engine/           # Command side (outbox writer)
│   └── src/.../domain, application, infrastructure
├── policy-query-service/    # Query side (consumer, ES, REST)
│   └── src/.../domain, application, infrastructure, interfaces
└── README.md
```

## 9. Configuration summary

| Component           | Key config |
|--------------------|------------|
| policy-engine      | `policy-engine.emit-interval-ms`, `spring.datasource.*` |
| policy-query-service | `policy-query.kafka.topic`, `policy-query.kafka.dlq-topic`, `policy-query.elasticsearch.index-prefix`, `spring.kafka.bootstrap-servers`, `spring.elasticsearch.uris` |
| Debezium           | `debezium-connector.json`: table `public.outbox`, topic `policy-events`, DLQ `policy-events-connect-dlq` |
