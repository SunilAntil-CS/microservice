# VIM Simulator

A **mock VIM** (OpenStack-like) implemented as a separate Spring Boot application. It exposes REST endpoints to allocate and release VMs and supports **configurable failure modes** and **latency** for testing.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| **POST** | /servers | Allocate a VM. Body: `{ "cpu": 2, "memory": 4096 }`. Returns 201 with `resourceId`, `ip`, `status`, `cpu`, `memory`. |
| **DELETE** | /servers/{id} | Release a VM. Returns 204. |
| **GET** | /servers/{id} | Get server status. Returns 200 with server details or 404. |

## Configuration

In `application.yml` (or override via env / system properties):

| Property | Description | Default |
|----------|-------------|---------|
| **failure.rate** | Probability of failure (0.0–1.0). Each request may fail with this probability. | 0.0 |
| **failure.error-types** | List of error types when failure triggers: e.g. `TIMEOUT`, `QUOTA`, `INTERNAL`. | TIMEOUT, QUOTA, INTERNAL |
| **latency.min-ms** | Min simulated delay in ms. | 0 |
| **latency.max-ms** | Max simulated delay in ms. | 100 |
| **pool.max-servers** | Max number of VMs in the in-memory pool. Allocations beyond this return QUOTA error. | 100 |

### Error types and HTTP status

- **TIMEOUT** → 504 Gateway Timeout  
- **QUOTA** → 403 Forbidden (e.g. pool full)  
- **INTERNAL** → 500 Internal Server Error  

## Run

```bash
mvn spring-boot:run
```

Server listens on **port 9090** by default. Override with `--server.port=9091` or `server.port` in `application.yml`.

### Examples

```bash
# Allocate a VM
curl -X POST http://localhost:9090/servers -H "Content-Type: application/json" -d '{"cpu":2,"memory":4096}'

# Get status
curl http://localhost:9090/servers/vm-<resourceId>

# Release
curl -X DELETE http://localhost:9090/servers/vm-<resourceId>
```

### Testing failure and latency

```bash
# 30% failure rate, 50–500 ms latency
mvn spring-boot:run -- -Dfailure.rate=0.3 -Dlatency.min-ms=50 -Dlatency.max-ms=500
```

All requests are **logged** (method, path, response status) by `RequestLoggingFilter`.
