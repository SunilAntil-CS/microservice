# Chapter 3: Microservices – Production-Grade Examples (Modules 1–5)

**All projects and modules in this repository belong to Chapter 3** (Sync, Discovery, Async, Reliability). Sub-projects are prefixed `ch3-` for easy identification.

- **Ch3 Module 1 + 2:** Server-Side Service Discovery (Kubernetes DNS), type-safe config, Circuit Breaker (NFVO ↔ VNFM).
- **Ch3 Module 3:** Transactional Outbox (Mediation Service: CDR → DB + outbox → Kafka).
- **Ch3 Module 4:** Idempotent Consumer (FMS: consume alarm events, one ticket per message_id via received_messages).
- **Ch3 Module 5:** gRPC (order-grpc: contract-first .proto, binary Protocol Buffers, high-performance internal IPC).

## Architecture

- **NFVO** (Orchestrator): Calls VNFM over HTTP using a **logical name** (`vnfm-service` in K8s, `vnfm` in Docker Compose). Uses `VnfmServiceDestinations` for config and Circuit Breaker for resilience.
- **VNFM** (Manager): Exposes `/vnf_instances/{id}/health`. In K8s, multiple replicas sit behind the `vnfm-service` Service; DNS + Kube-Proxy do discovery and load balancing.

```
┌─────────────┐     http://vnfm-service/...      ┌──────────────────┐
│    NFVO     │ ──────────────────────────────► │  vnfm-service     │
│  (port 8080)│   (DNS → ClusterIP → Pods)      │  (K8s Service)   │
└─────────────┘                                  └────────┬─────────┘
                                                         │
                                            ┌────────────┼────────────┐
                                            ▼            ▼            ▼
                                         [VNFM Pod] [VNFM Pod]  [VNFM Pod]
```

## Chapter 3 – Project Layout

```
chapter3-microservices/
├── ch3-nfvo/                # Ch3 Module 1+2: NFVO (consumer, circuit breaker, discovery)
├── ch3-vnfm/                # Ch3 Module 2: VNFM (discovery target)
├── ch3-mediation/           # Ch3 Module 3: Mediation (Transactional Outbox)
├── ch3-fms/                 # Ch3 Module 4: FMS (Idempotent Consumer)
├── ch3-order-grpc/          # Ch3 Module 5: gRPC (proto + @GrpcService server)
│   ├── src/main/proto/OrderService.proto
│   └── grpc/OrderServiceServer.java
├── k8s/
├── docker-compose.yml
├── docs/
│   ├── Ch3-CODE-REFERENCE.md           # Ch3: Annotations, types, other-framework equivalents
│   ├── Ch3-MODULE-3-OUTBOX.md          # Ch3 Module 3: Transactional Outbox notes + interview
│   ├── Ch3-MODULE-4-IDEMPOTENT-CONSUMER.md  # Ch3 Module 4: Idempotent Consumer notes + interview
│   └── Ch3-MODULE-5-GRPC.md            # Ch3 Module 5: gRPC notes + REST vs gRPC interview
└── README.md
```

## Learning: comments and reference

- **In-code comments**: Every Spring annotation, inbuilt class (e.g. `WebClient`, `Mono`, `Logger`), and important types (e.g. `record`, `ConcurrentHashMap`) are explained in the Java and properties files. They double as notes for microservice and resilience concepts.
- **Cross-framework**: Where relevant, comments mention how the same idea is done in other frameworks (e.g. Quarkus, Micronaut, Node, Go). See **docs/Ch3-CODE-REFERENCE.md** for a table of annotations and types with short equivalents elsewhere.

## Quick Start

### Option A: Local (two terminals, no Docker)

**Terminal 1 – VNFM (dev profile → port 8081)**

```bash
cd ch3-vnfm && mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Listens on http://localhost:8081
```

**Terminal 2 – NFVO (points to localhost VNFM)**

```bash
cd ch3-nfvo && mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Listens on http://localhost:8080, uses http://localhost:8081 for VNFM
```

Then:

- VNFM: `curl http://localhost:8081/vnf_instances/vnf-1/health`
- NFVO: `curl http://localhost:8080/api/v1/vnf/vnf-1/health`

(Dev: VNFM on 8081, NFVO on 8080; NFVO uses `application-dev.properties` → `vnfm.service.url=http://localhost:8081`.)

### Option B: Docker Compose (service name = discovery)

```bash
docker compose up --build
```

- NFVO: `http://localhost:8080` (profile `docker` → `vnfm.service.url=http://vnfm:8080`)
- VNFM: `http://localhost:8081`

```bash
curl http://localhost:8080/api/v1/vnf/vnf-1/health
```

### Option C: Kubernetes

1. Build and load images (e.g. into kind/minikube):

   ```bash
   docker build -t vnfm:1.0.0 ./ch3-vnfm
   docker build -t nfvo:1.0.0 ./ch3-nfvo
   kind load docker-image vnfm:1.0.0 nfvo:1.0.0   # if using kind
   ```

2. Deploy (order matters: Service before Deployment is fine):

   ```bash
   kubectl apply -f k8s/vnfm-service.yml
   kubectl apply -f k8s/vnfm-deployment.yml
   kubectl apply -f k8s/nfvo-deployment.yml
   kubectl apply -f k8s/nfvo-service.yml
   ```

3. NFVO uses `vnfm.service.url=http://vnfm-service` (default in `application.properties`). In-cluster, `vnfm-service` resolves to the VNFM Service ClusterIP; no IPs in code.

4. Access NFVO (e.g. port-forward or LoadBalancer):

   ```bash
   kubectl port-forward svc/nfvo-service 8080:80
   curl http://localhost:8080/api/v1/vnf/vnf-1/health
   ```

## Configuration (Module 2)

- **Type-safe config:** `VnfmServiceDestinations` with `@ConfigurationProperties(prefix = "vnfm.service")` and `@NotBlank` so the app fails fast if `vnfm.service.url` is missing.
- **Environments:**
  - Default: `vnfm.service.url=http://vnfm-service` (Kubernetes).
  - Dev: `application-dev.properties` → `http://localhost:8081`.
  - Docker: `application-docker.properties` → `http://vnfm:8080`.

K8s can override with env:

```yaml
env:
  - name: VNFM_SERVICE_URL
    value: "http://vnfm-service"
```

## Resilience (Module 1)

- Circuit Breaker `vnfm-circuit`: 50% failure rate over 5 calls opens the circuit; 5s wait before half-open.
- Timeout 2s on VNFM calls; fallback returns `{"vnfId":"...", "status":"UNKNOWN", "message":"VNFM Unreachable"}` so the dashboard never blocks.

## Ch3 Module 3: Transactional Outbox (Mediation)

- **Service:** `ch3-mediation` — receives raw CDRs via POST `/api/v1/cdr`, saves to DB and to outbox table in **one transaction**; relay job publishes outbox to Kafka.
- **Notes file:** **docs/Ch3-MODULE-3-OUTBOX.md** — pattern summary, schema, key terms, and interview Q&A (Dual Write Problem, atomicity, CDC, idempotent consumers).
- **Run:** `cd ch3-mediation && mvn spring-boot:run` (port 8082). Without Kafka, relay will log send failures and retry. With Kafka on `localhost:9092`, events go to topic `billing-events`.
- **Schema:** `ch3-mediation/src/main/resources/sql/schema.sql` — `cdr` and `message` (outbox) tables; JPA can also create via `ddl-auto=update`.

## Ch3 Module 4: Idempotent Consumer (FMS)

- **Service:** `ch3-fms` — consumes from Kafka topic `alarm-events`; creates **one** trouble ticket per unique `message_id` using a `received_messages` deduplication table in the **same transaction** as the ticket insert.
- **Notes file:** **docs/Ch3-MODULE-4-IDEMPOTENT-CONSUMER.md** — at-least-once, idempotency, ACID guard, DLQ, table bloat.
- **Run:** Start Kafka, then `cd ch3-fms && mvn spring-boot:run` (port 8083). Simulate: `POST /api/v1/alarms/simulate` with body `{"nodeId":"node-1","linkId":"link-1","messageId":"test-1"}`. Call twice with same `messageId` → only one ticket. List tickets: `GET /api/v1/tickets`.
- **Schema:** `ch3-fms/src/main/resources/sql/schema.sql` — `received_messages` (PK consumer_id, message_id) and `trouble_ticket`.

## Ch3 Module 5: gRPC (order-grpc)

- **Service:** `ch3-order-grpc` — contract-first: `.proto` defines `OrderService` and messages; Maven generates Java/gRPC stubs; server implements `OrderServiceGrpc.OrderServiceImplBase` and calls business logic. Binary Protocol Buffers over HTTP/2 for high-throughput internal calls.
- **Notes file:** **docs/Ch3-MODULE-5-GRPC.md** — REST vs gRPC, when to use gRPC, contract-first, grpcurl.
- **Run:** `cd ch3-order-grpc && mvn compile && mvn spring-boot:run` (HTTP 8084, gRPC **9090**). Test with grpcurl: `grpcurl -plaintext -d '{"restaurant_id":1,"consumer_id":2,"line_items":[{"menu_item_id":"item-1","quantity":2}]}' localhost:9090 com.telecom.ordergrpc.grpc.OrderService/createOrder`.

## Production Notes

- **Decoupling:** Code uses only the logical name (`vnfm-service` / `vnfm`); no IPs or instance counts.
- **Discovery:** In K8s, CoreDNS resolves the Service name to ClusterIP; Kube-Proxy load-balances to healthy Pods.
- **Health:** Apps expose `/actuator/health`; K8s deployments use liveness/readiness probes.
- **Config:** Single place for the VNFM URL (`VnfmServiceDestinations`); easy to test and override per environment.
- **Outbox:** Same DB for CDR and outbox = one transaction = no Dual Write; relay gives at-least-once → consumers must be idempotent (Module 4).
- **Idempotency (Module 4):** Same DB transaction for `received_messages` insert and business insert; duplicate key → skip. No Redis/cache for dedup (race condition).
