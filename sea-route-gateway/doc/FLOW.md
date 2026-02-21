# Sea Route Gateway – Implementation Flow

This document describes the implementation steps for the Sea Route API Gateway.

---

## Step 1: Project Setup

### Project structure

```
sea-route-gateway/
├── src/main/java/com/searoute/gateway/
│   ├── config/          # Configuration classes (e.g. Redis, WebClient, Gateway)
│   ├── filter/          # Custom gateway filters (e.g. rate limiting, logging)
│   ├── handler/         # Custom handlers for composition
│   ├── proxy/           # WebClient proxies to backend services
│   ├── dto/             # Data transfer objects
│   └── SeaRouteGatewayApplication.java
├── src/main/resources/
│   └── application.yml
├── src/test/java/com/searoute/gateway/
│   └── SeaRouteGatewayApplicationTests.java
├── pom.xml
└── FLOW.md
```

### Chosen dependencies

| Dependency | Purpose |
|------------|---------|
| **Spring Cloud Gateway** | Reactive API gateway; routing, filtering, load balancing. |
| **Spring Boot Actuator** | Health checks, metrics, and gateway endpoint exposure. |
| **Spring Cloud Starter Netflix Eureka Client** | Service discovery and registration with Eureka. |
| **Spring Boot Starter Data Redis Reactive** | Reactive Redis client for rate limiting and response caching. |
| **Lombok** | Reduces boilerplate (e.g. getters, builders). |
| **Spring Boot Starter Test** | JUnit 5 and Spring Test support. |
| **Reactor Test** | Testing reactive streams (e.g. `StepVerifier`). |

### Versions

- **Spring Boot:** 3.3.x (versions managed by `spring-boot-starter-parent`).
- **Spring Cloud:** 2023.0.x (Leyton), compatible with Spring Boot 3.3.x.

### Delivered in this step

- `pom.xml` with the above dependencies and version management.
- Main application class `SeaRouteGatewayApplication` with `@SpringBootApplication`.
- Empty `application.yml` with commented placeholders for server, gateway, Eureka, Redis, and Actuator.
- A simple `@SpringBootTest` that verifies the application context loads.

---

## Step 2: Basic Routing

### Routing rules

Routes are defined in `config/RouteConfig` via a `RouteLocator` bean:

| Path pattern | Backend (fixed URI for now) | With Eureka (later) |
|--------------|-----------------------------|----------------------|
| `/api/v1/bookings/**` | `http://localhost:8081` | `lb://BOOKING-SERVICE` |
| `/api/v1/tracking/**` | `http://localhost:8082` | `lb://CARGO-TRACKING-SERVICE` |
| `/api/v1/schedules/**` | `http://localhost:8083` | `lb://VESSEL-SCHEDULE-SERVICE` |

Each route adds the request header `X-Gateway-Source: sea-route` so backends can identify traffic from the gateway.

### How it works

- **Predicates:** The `path("/api/v1/.../**")` predicate matches the request path; the first matching route is used.
- **Filters:** The `addRequestHeader` filter runs before the request is forwarded to the backend.
- **URI:** Without service discovery we use fixed `http://localhost:PORT` URIs. With Eureka, `lb://SERVICE-ID` enables client-side load balancing over discovered instances.

### Testing

1. Start the gateway (default port 8080).
2. Start a mock backend on the expected port (e.g. a simple Spring Boot or HTTP server on 8081 that serves `GET /api/v1/bookings/...`).
3. Call the gateway with curl or Postman, e.g.:
   - `curl -v http://localhost:8080/api/v1/bookings/123`
   The request is forwarded to the backend; the backend receives the `X-Gateway-Source: sea-route` header.
4. When Eureka is configured, switch the URIs in `RouteConfig` to `lb://BOOKING-SERVICE`, etc., and ensure the gateway can resolve and reach the registered services.

---

## Step 3: Correlation ID and Logging

### Implementation

- **Correlation ID filter** (`filter/CorrelationIdFilter`): A `GlobalFilter` with `Ordered.HIGHEST_PRECEDENCE` that runs first. It reads the `X-Correlation-ID` request header; if missing, it generates a UUID. The ID is stored in the exchange attribute `correlationId` and added to the outgoing request via `exchange.mutate().request(...)`, so downstream services receive the same ID. In reactive WebFlux we use exchange attributes to pass the ID to the logging filter; full MDC propagation would require Reactor's `Context` (e.g. with a context writer and logback-reactor).
- **Logging filter** (`filter/LoggingFilter`): A `GlobalFilter` with order `HIGHEST_PRECEDENCE + 1` so it runs after the correlation filter. It records the start time before calling the chain, then in a `.then(Mono.fromRunnable(...))` callback (after the chain completes) it logs method, path, response status, duration, and correlation ID in a structured format (e.g. `method=GET path=/api/v1/bookings/123 status=200 durationMs=45 correlationId=...`).
- **Structured JSON logging**: The `logstash-logback-encoder` dependency (v7.4) and `logback-spring.xml` configure a console appender with `LogstashEncoder`, so each log line is a JSON object suitable for log aggregators (ELK, Splunk). The encoder can include MDC key `correlationId` when present.

### Filter order

`Ordered` is used so that the correlation ID is set before any logging or other filters that might need it.

### Testing

1. Send a request **without** `X-Correlation-ID`: the gateway generates one and forwards it to the backend; the response and gateway logs should show the same ID.
2. Send a request **with** `X-Correlation-ID: my-id-123`: the gateway reuses it and propagates it; logs should show `correlationId=my-id-123`.
3. Check gateway logs: each request should produce a structured log line (JSON if using the provided logback config) with `method`, `path`, `status`, `durationMs`, and `correlationId`.
