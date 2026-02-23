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

---

## Step 4: Authentication

### JWT validation flow

1. **Dependency**: `spring-boot-starter-oauth2-resource-server` provides JWT validation support and integrates with Spring Security WebFlux.
2. **Configuration** (`application.yml`): The issuer URI (e.g. `http://localhost:8080/auth/realms/searoute` for a development Keycloak realm) is set under `spring.security.oauth2.resourceserver.jwt.issuer-uri`. The decoder uses OpenID Connect discovery to resolve the JWK Set URI; an optional `jwk-set-uri` can override this. Public keys from the JWK Set are **cached automatically** by the decoder, so repeated requests do not hit the IdP for key lookup.
3. **Security** (`config/SecurityConfig`): A `SecurityWebFilterChain` bean is defined with `@EnableWebFluxSecurity`. Routes matching `/actuator/health` are permitted without authentication; all other exchanges require an authenticated JWT via `.anyExchange().authenticated()`. OAuth2 resource server with JWT is enabled (`.oauth2ResourceServer().jwt()`), so the `Authorization: Bearer <token>` header is validated (signature, issuer, expiry) and the principal is set in the security context.
4. **User context propagation** (`filter/JwtAuthenticationFilter`): A `GlobalFilter` runs after the security filter chain. When the principal is a JWT, the filter reads the subject (`sub`) and roles (from `realm_access.roles`, `roles`, or `scope` claims, depending on the IdP) and adds headers `X-User-ID` and `X-User-Roles` to the outgoing request. Downstream services can use these headers to identify the caller without parsing the token; they can trust the values because the gateway has already validated the JWT.
5. **Error responses**: Custom handlers (see Step 4a) return consistent JSON for 401 and 403; the JWT filter's `writeJsonError` helper remains available for filters that need to write JSON errors.

### Summary

| Component | Role |
|-----------|------|
| **SecurityWebFilterChain** | Permits `/actuator/health`; requires authentication for all other routes; enables OAuth2 resource server JWT validation. |
| **JwtAuthenticationFilter** | After authentication, adds `X-User-ID` and `X-User-Roles` from JWT claims so backends receive user context. |
| **ReactiveJwtDecoder** | Configured from `issuer-uri`; caches public keys; used by the resource server to validate Bearer tokens. |

### Testing

1. **Without token**: `curl -v http://localhost:8080/api/v1/bookings/123` → expect `401 Unauthorized` (or a JSON error body if customized).
2. **With valid token**: Generate a test JWT (e.g. at [jwt.io](https://jwt.io) with the correct `iss`, `exp`, and signature, or obtain one from your IdP). Then: `curl -v -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/bookings/123` → request should be forwarded; backend should receive `X-User-ID` and `X-User-Roles`.
3. **Health without token**: `curl -v http://localhost:8080/actuator/health` → expect `200 OK` without a Bearer token.
4. **Invalid or expired token**: Use a bad or expired JWT in the `Authorization` header → expect `401 Unauthorized`.

---

## Step 4a: Custom Error Response Handlers

### Why they are needed

Spring Security's default behaviour for authentication and authorization failures is to return HTML error pages or empty bodies. For an API gateway, clients expect **consistent JSON error responses** that match the design document's standard format. Without custom handlers, 401 (missing/invalid token) and 403 (insufficient permissions) would not be machine-readable or aligned with the rest of the API.

### How they work

1. **CustomAuthenticationEntryPoint** (`security/CustomAuthenticationEntryPoint`): Implements `ServerAuthenticationEntryPoint`. When a request reaches a protected resource without a valid JWT (or with an invalid/expired token), the resource server calls `commence`. The handler builds a JSON body with `timestamp`, `status` (401), `error` ("Unauthorized"), `message` (from the exception or a generic "Authentication required"), and `path`. It sets the response status to 401, content type to `application/json`, and cache control to `no-store`, then writes the JSON to the response body via `ObjectMapper`.

2. **CustomAccessDeniedHandler** (`security/CustomAccessDeniedHandler`): Implements `ServerAccessDeniedHandler`. When an authenticated user (valid JWT) tries to access a resource they are not allowed to (e.g. a path protected by `.hasRole("ADMIN")` and the user lacks that role), Spring Security calls `handle`. The handler produces the same JSON shape but with `status` 403, `error` "Forbidden", and a fixed message such as "You do not have permission to access this resource".

3. **SecurityConfig**: The `SecurityWebFilterChain` bean injects both handlers. The OAuth2 resource server is configured with `.authenticationEntryPoint(customAuthenticationEntryPoint)` so all authentication failures use the custom 401 JSON. Exception handling is configured with `.accessDeniedHandler(customAccessDeniedHandler)` so all authorization failures use the custom 403 JSON.

### Result

All security-related errors from the gateway now return a predictable JSON structure (timestamp, status, error, message, path), meeting production expectations and enabling clients to parse and display errors consistently.

---

## Step 5: Rate Limiting

### Overview

The gateway uses Spring Cloud Gateway's built-in **RequestRateLimiter** filter with **Redis** as the backend. This provides distributed rate limiting: limits are stored in Redis, so multiple gateway instances share the same state and each client is limited consistently regardless of which instance handles the request.

### Token bucket algorithm

Rate limiting is implemented with the **token bucket** algorithm:

- Each client (identified by the key from the key resolver) has a bucket that can hold up to **burstCapacity** tokens (e.g. 20).
- Each request consumes **requestedTokens** (default 1) from the bucket.
- Tokens are **replenished** at **replenishRate** per second (e.g. 10/s).
- If a request arrives and the bucket does not have enough tokens, the gateway responds with **429 Too Many Requests**.
- Setting `replenishRate == burstCapacity` gives a steady rate; setting `burstCapacity > replenishRate` allows short bursts, with refill at the sustained rate between bursts.

### Configuration

1. **Redis** (`application.yml`): Under `spring.data.redis`, set `host` (e.g. `localhost`) and `port` (e.g. `6379`). Ensure Redis is running (e.g. `docker run -p 6379:6379 redis`).

2. **Rate limit headers**: Set `spring.cloud.gateway.filter.request-rate-limiter.include-headers=true` so responses include `X-RateLimit-Remaining`, `X-RateLimit-Requested-Tokens`, etc., for client visibility.

3. **RateLimiterConfig** (`config/RateLimiterConfig`):
   - **KeyResolver** bean (`userKeyResolver`): Resolves the rate-limit key per request. If the `X-User-ID` header is present (set by the JWT filter after authentication), that value is used so limits are per user; otherwise the client IP is used. This ensures each client is limited independently and authenticated users get a consistent limit by identity.
   - **RedisRateLimiter** bean: Created with `replenishRate` (e.g. 10) and `burstCapacity` (e.g. 20). Used by the RequestRateLimiter filter on each route.

4. **RouteConfig**: Each `/api/v1/bookings/**`, `/api/v1/tracking/**`, and `/api/v1/schedules/**` route applies the RequestRateLimiter filter with the configured key resolver and Redis rate limiter (same replenish rate and burst capacity for all these routes).

### Testing

1. Start Redis: `docker run -p 6379:6379 redis`.
2. Start the gateway (and backends if needed).
3. Send many requests quickly to a protected path (e.g. `curl` in a loop or a load tool). After exceeding the limit (e.g. burst of 20 then more within the same second), responses should be **429 Too Many Requests**.
4. Inspect response headers for `X-RateLimit-*` when `include-headers` is true.
5. With a valid JWT, the limit is per user (X-User-ID); without it, the limit is per client IP.

---

## Step 6: Service Proxies (WebClient and Resilience)

### Purpose

Backend calls are encapsulated in **reactive proxy classes** (e.g. `BookingServiceProxy`, `CargoTrackingServiceProxy`, `VesselScheduleServiceProxy`) that use `WebClient`. A **separate proxy layer** isolates gateway logic from backend communication details: URLs, timeouts, retries, and error handling live in the proxy so that route and filter logic stays simple. Proxies are used when the gateway needs to call a backend programmatically (e.g. for aggregation or custom handlers) rather than only forwarding requests.

### WebClient and timeouts

- **WebClientConfig** (`config/WebClientConfig`) defines a shared `HttpClient` and one `WebClient` bean per backend (booking, tracking, schedule). Base URLs come from `searoute.backend.*` in `application.yml`.
- **Timeouts are set on the WebClient instance** via the underlying `HttpClient`: connect timeout (e.g. 5s), response/read/write timeout (e.g. 10s), and a fixed connection pool. This ensures the gateway does **not hang indefinitely** when a backend is slow or unresponsive; timed-out calls are treated as failures and handled by the circuit breaker fallback.

### Why two layers: WebClient timeouts and circuit breaker

We use **two layers** so the gateway does not get stuck: (1) **HTTP-level timeouts** in WebClientConfig, and (2) the **circuit breaker** in the proxy. They do different jobs; the circuit breaker alone cannot replace WebClient timeouts.

- **Circuit breaker** does *not* stop a single request from hanging. It only **reacts to failures** (errors, timeouts, 5xx). After a threshold of failures it opens and returns the fallback without calling the backend. So it needs something to **produce** those failures.
- **Without HTTP timeouts**, a slow or stuck backend would never complete and would never clearly “fail”—the request would just hang. The circuit breaker would not see a timeout, would not release the connection or the reactive pipeline, and could not open based on that call. Connections and resources would stay held.
- **With HTTP timeouts**, the WebClient aborts the request after e.g. 10s, releases the connection, and the `Mono` fails with a timeout. That failure is what the circuit breaker counts; it can then trigger the fallback for that call and, after enough such failures, open the circuit so subsequent calls fail fast without hitting the backend.

So: **WebClient timeouts** create the failure and free resources for each stuck call; the **circuit breaker** uses those failures to protect the gateway from cascading load and returns the fallback. Both layers are needed.

### Circuit breakers

- Each proxy wraps `WebClient` calls with a Resilience4J **circuit breaker** via `ReactiveCircuitBreakerFactory.create("name").run(call, fallback)`.
- The circuit breaker **prevents cascading failures** when a backend is slow or down: after a threshold of failures it opens and returns the fallback (e.g. `Booking.empty()`) immediately instead of calling the backend, so the gateway remains responsive.
- Fallbacks return a consistent DTO shape (e.g. `status = "UNAVAILABLE"`) so callers can handle degradation gracefully.

### Where circuit breaker properties are defined and how Spring hands them to the proxy

**1. Where you define the properties**

Circuit breaker settings are defined per **instance name** in `application.yml` (or `application-<profile>.yml`):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      bookingService:      # ← must match factory.create("bookingService") in BookingServiceProxy
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
      trackingService:     # ← must match factory.create("trackingService") in CargoTrackingServiceProxy
        # ...
      scheduleService:     # ← must match factory.create("scheduleService") in VesselScheduleServiceProxy
        # ...
```

The **instance name** (e.g. `bookingService`) is the key under `resilience4j.circuitbreaker.instances`. It must match the string you pass to `circuitBreakerFactory.create("bookingService")` in the proxy. Each proxy uses one instance name; that name links the YAML config to the circuit breaker used by that proxy.

**2. How Spring picks up the properties**

- The **spring-cloud-starter-circuitbreaker-reactor-resilience4j** dependency brings in Spring Boot auto-configuration for Resilience4j.
- Spring Boot’s configuration binding reads `resilience4j.circuitbreaker.instances.*` and builds a map of instance name → Resilience4j `CircuitBreakerConfig` (or equivalent configuration object).
- The **ReactiveResilience4JCircuitBreakerFactory** bean is created by that auto-configuration and is given access to this configuration (e.g. via `CircuitBreakerConfigurationProperties` or similar). So the factory “knows” the settings for each instance name.

**3. How the properties reach your proxy via ReactiveCircuitBreakerFactory**

- Your proxy does **not** define circuit breaker config in code. It only injects `ReactiveCircuitBreakerFactory` and calls `create("bookingService")`.
- When the proxy calls `circuitBreakerFactory.create("bookingService")`, the factory (the Resilience4j implementation) looks up the configuration for the instance name **"bookingService"** from the bound properties.
- If it finds `resilience4j.circuitbreaker.instances.bookingService` in the environment, it uses those settings (slidingWindowSize, failureRateThreshold, etc.) to build or get the underlying Resilience4j `CircuitBreaker` for that name. If it doesn’t find instance-specific config, it falls back to global Resilience4j defaults (or Spring’s defaults).
- The factory returns a reactive circuit breaker that wraps the Resilience4j `CircuitBreaker` for that name. When the proxy then calls `.run(call, fallback)`, that breaker uses the config that was bound for `bookingService`.

So: **you define properties in YAML under `resilience4j.circuitbreaker.instances.<name>`; Spring binds them and supplies them to the factory; the factory uses the config for `<name>` when you call `create("<name>")` and hands you a breaker that behaves according to those properties.**

**Instance names are user-defined and must match in both places**

- Names like `bookingService`, `trackingService`, `scheduleService` are **chosen by you**; the framework does not fix them. You could use `booking`, `booking-api`, or any other string.
- The **same name** must be used in **two places**, or the factory will not find instance-specific config and will use defaults:
  1. **Config:** the key under `resilience4j.circuitbreaker.instances.<name>` in YAML.
  2. **Code:** the argument to `circuitBreakerFactory.create("name")` in the proxy.

| Proxy | `create("...")` in code | YAML config key |
|-------|--------------------------|------------------|
| BookingServiceProxy | `"bookingService"` | `instances.bookingService` |
| CargoTrackingServiceProxy | `"trackingService"` | `instances.trackingService` |
| VesselScheduleServiceProxy | `"scheduleService"` | `instances.scheduleService` |

### DTOs and proxies

- **DTOs** (`dto/Booking`, `Tracking`, `VesselSchedule`) model backend responses and provide static `empty()` methods for fallbacks.
- **Proxies** inject the corresponding `WebClient` (with `@Qualifier`) and `ReactiveCircuitBreakerFactory`, and expose methods such as `getBooking(id)`, `getTracking(id)`, `getSchedule(id)`.

### Testing

- **BookingServiceProxyTest**: Uses **WireMock** to mock the backend. Tests (1) success when backend returns 200, (2) fallback when backend returns 500, (3) fallback when backend is slow (timeout), (4) circuit breaker opens after repeated failures and subsequent calls get fallback.
- **GatewayWebTestClientTest**: Uses **WebTestClient** to call the gateway (e.g. `/actuator/health`) and assert status and body.
- Test profile `application-test.yml` configures Resilience4J so the circuit breaker opens quickly in tests (e.g. `minimumNumberOfCalls=2`, `failureRateThreshold=50`).
