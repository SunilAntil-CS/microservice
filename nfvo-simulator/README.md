# NFVO Simulator

A simple **command-line client** that calls the LCM REST API (`/api/vnfs`) as if it were an NFVO issuing VNF lifecycle operations. Use it to trigger instantiation, termination, and to query status or list VNFs. It supports **idempotency testing** via `--requestId` and optional status polling via `--delay`.

## Prerequisites

- **LCM Core** (chapter-6-lcm-core) running, e.g. at `http://localhost:8080`.
- Java 17+.

## Build

From the `nfvo-simulator` directory:

```bash
mvn clean package -DskipTests
```

The runnable JAR is at `target/nfvo-simulator-1.0.0-SNAPSHOT.jar`.

## Run

**Default LCM base URL** is `http://localhost:8080`. Override with `--baseUrl <url>`.

### Commands

| Command | Description |
|--------|-------------|
| **instantiate** \<vnf-type\> \<cpu\> \<memory\> [--requestId \<id\>] [--delay] | Create and start instantiation of a new VNF. Sends POST /api/vnfs. With `--delay`, polls status until a terminal state. |
| **terminate** \<vnf-id\> [--requestId \<id\>] | Request termination of a VNF. Sends DELETE /api/vnfs/{vnfId}. |
| **status** \<vnf-id\> | Get current state of a VNF. Sends GET /api/vnfs/{vnfId}/status. |
| **list** | List all VNFs. Sends GET /api/vnfs. |

### Options

- **--baseUrl** \<url\> – LCM base URL (default: `http://localhost:8080`).
- **--requestId** \<id\> – Idempotency key. Send the same requestId twice to verify LCM returns the cached response without re-executing (instantiate and terminate).
- **--delay** – After **instantiate**, poll the VNF status every 2 seconds until state is INSTANTIATED, ACTIVE, FAILED, or TERMINATED (or timeout).

### Examples

```bash
# Run with Maven (from nfvo-simulator directory)
mvn spring-boot:run -- -q instantiate my-vnf 2 4

# Or run the JAR
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar instantiate my-vnf 2 4

# With idempotency key (duplicate calls return same response)
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar instantiate my-vnf 2 4 --requestId req-123

# Instantiate and poll status until terminal state
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar instantiate my-vnf 2 4 --delay

# Custom LCM URL
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar --baseUrl http://localhost:8080 list

# Terminate with idempotency
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar terminate 550e8400-e29b-41d4-a716-446655440000 --requestId req-456

# Status and list
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar status 550e8400-e29b-41d4-a716-446655440000
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar list
```

### Idempotency testing

1. Run **instantiate** with a fixed **--requestId** (e.g. `req-abc`). Note the returned `vnfId` and HTTP 202.
2. Run the **same** command again (same requestId). LCM should return the **same** 202 and body (cached response) without creating a second VNF.
3. Similarly, use **--requestId** on **terminate** and send the same request twice; the second call should return the cached response.

## Output

Responses are printed with HTTP status, optional **Location** header, and the response body pretty-printed as JSON.
