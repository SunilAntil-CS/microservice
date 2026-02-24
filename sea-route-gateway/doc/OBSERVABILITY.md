# Observability: Metrics (Prometheus) and Distributed Tracing (Zipkin)

This document describes the observability setup for the Sea Route Gateway: **metrics** (Micrometer + Prometheus) and **distributed tracing** (Brave + Zipkin). It includes concepts, configuration, and a Q&A section based on common questions.

---

## 1. Overview

| Concern | Technology | Purpose |
|--------|------------|--------|
| **Metrics** | Micrometer + Prometheus | Monitor system health and performance: throughput (call rate), latency, errors. Prometheus scrapes metrics for dashboards and alerts. |
| **Tracing** | Brave + Zipkin | Track a single request across the gateway and downstream services (booking, cargo, payment). Essential for debugging latency and understanding call flows in a multi-service system. |

The two are **independent pipelines**: metrics are stored in memory and scraped by Prometheus; traces are spans pushed to Zipkin. They complement each other (metrics for trends and alerts, tracing for request-level detail).

---

## 2. Metrics (Micrometer + Prometheus)

### 2.1 Concepts

- **Micrometer** is the metrics facade used by Spring Boot. It records counters, timers, gauges, etc. in memory.
- **Prometheus** is a monitoring system that **pulls** (scrapes) metrics from the application at a configured endpoint.
- The gateway exposes metrics in Prometheus format at **`/actuator/prometheus`**. Prometheus periodically scrapes this URL and stores the time series for querying and alerting.

### 2.2 Dependencies

```xml
<!-- Spring Boot Actuator (exposes /actuator/*) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus registry: formats metrics for Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2.3 Configuration (`application.yml`)

- **management.endpoints.web.exposure**: Include `health`, `metrics`, and `prometheus` so that:
  - `/actuator/health` — health checks
  - `/actuator/metrics` — list and inspect metrics
  - `/actuator/prometheus` — Prometheus scrape endpoint
- Prometheus is configured separately (in Prometheus config) to scrape `http://<gateway-host>:8080/actuator/prometheus` on an interval.

### 2.4 Custom metrics in this project

**BookingSummaryHandler** injects **MeterRegistry** and records:

| Metric | Type | Meaning |
|--------|------|--------|
| **booking.summary.calls** | Counter | Incremented on each summary request (call rate). |
| **booking.summary.duration** | Timer | Duration of each summary call (latency). |

These appear at `/actuator/prometheus` with names such as `booking_summary_calls_total` and `booking_summary_duration_*`. Prometheus scrapes them; no push from the app to Prometheus.

### 2.5 Pipeline (pull model)

```
Application (MeterRegistry)  →  /actuator/prometheus  ←  Prometheus (scrape)
```

---

## 3. Distributed Tracing (Zipkin)

### 3.1 Concepts

- A **trace** represents one logical request end-to-end (e.g. one HTTP request to the gateway and all downstream calls). It is identified by a **trace ID**.
- A **span** is one unit of work within a trace (e.g. “handle request”, “GET booking service”, “merge summary”). Each span has a **span ID**, optional **parent span ID**, name, start time, duration, and tags.
- **Zipkin** is a trace backend. Applications **push** spans to Zipkin; Zipkin stores them and provides a UI to view traces.
- **Brave** is the tracing library; **Micrometer Tracing** bridges Brave to the Spring ecosystem and provides the `Tracer` bean. Spring Cloud Gateway and WebClient are instrumented so that incoming requests and outbound HTTP calls create spans and propagate **X-B3-* headers** (trace ID, span ID, sampling) to downstream services.

### 3.2 Dependencies

```xml
<!-- Micrometer Tracing bridge to Brave; provides Tracer and context propagation -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Send spans to Zipkin -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### 3.3 Configuration (`application.yml`)

- **management.tracing.sampling.probability**: e.g. `1.0` in development (sample all requests); use a lower value (e.g. `0.1`) in production to reduce overhead.
- **management.zipkin.tracing.endpoint**: e.g. `http://localhost:9411/api/v2/spans` so the gateway sends spans to Zipkin.

### 3.4 What is auto-instrumented

- **Incoming request** at the gateway: a span is created (e.g. by the tracing filter) when the request enters and ended when the response is sent.
- **Outbound HTTP (WebClient)**: each call to booking, cargo, or payment service creates a **child span**. X-B3-* headers are added so downstream services can join the same trace.

No Zipkin-specific code is required in handlers for this; the gateway and WebClient integrations handle it.

### 3.5 Custom span in this project

**BookingSummaryHandler** injects **Tracer** and adds a custom span **`summary-merge`** around the step that merges booking, cargo, and invoice into `BookingSummaryResponse`. That step is in-memory only (no HTTP call), so it would not show up in Zipkin otherwise. The custom span makes composition/merge time visible in the trace.

### 3.6 Pipeline (push model)

```
Application (Brave/Tracer)  →  Zipkin HTTP endpoint  →  Zipkin server (store + UI)
```

### 3.7 What Zipkin stores (per request)

For each request, Zipkin stores:

- **One trace** (identified by trace ID).
- **One or more spans** (tree structure). For each span it stores:
  - Trace ID, Span ID, Parent span ID
  - Name (e.g. `get`, `summary-merge`, `GET`)
  - Start timestamp, duration
  - Local endpoint (service name, e.g. `sea-route-gateway`)
  - Remote endpoint (optional; for client spans)
  - Tags (e.g. `http.method`, `http.status_code`, `http.url`, `error`)

Zipkin does **not** store:

- Request/response bodies
- Full headers (unless added as tags)
- Application logs (unless you add them as span tags or events)

### 3.8 Hierarchical structure

Zipkin traces are **hierarchical**: a trace is a **tree of spans**.

- **Root span**: the top-level operation (e.g. gateway handling the request).
- **Child spans**: each has a parent span ID; e.g. WebClient calls and the custom `summary-merge` span are children of the gateway span.

The Zipkin UI shows this as a timeline and/or a tree view.

---

## 4. Metrics vs tracing: two pipelines

| | Metrics (Prometheus) | Tracing (Zipkin) |
|---|----------------------|-------------------|
| **Data** | Counters, timers, gauges (aggregated) | Spans (per-request tree) |
| **Source in app** | MeterRegistry (in-memory) | Brave Tracer / spans |
| **Flow** | Prometheus **scrapes** `/actuator/prometheus` | App **pushes** spans to Zipkin |
| **Use case** | Dashboards, alerts, SLOs | Debugging latency, understanding call flow |

Handler code that uses **MeterRegistry** (e.g. counter + timer in BookingSummaryHandler) only updates Micrometer metrics. That data goes to Prometheus when Prometheus scrapes. Zipkin receives data from **Brave spans** (gateway filter, WebClient, custom spans), not from MeterRegistry.

---

## 5. Related: circuit breaker and timeouts

- **Circuit breaker** (e.g. Resilience4j): wraps the **Mono** (the WebClient call). The factory creates breakers by name; the name must match the configuration (e.g. `resilience4j.circuitbreaker.instances.<name>` in YAML and `factory.create("name")` in code).
- **WebClient timeouts**: create failures and free resources when a call is slow.
- **Both** are useful: timeouts cause failures and limit resource use; the circuit breaker reacts to those failures and opens after a threshold.

---

## 6. When to add custom spans

Add custom spans when you want to see logical steps in Zipkin that are not already instrumented, for example:

- **In-memory or non-HTTP work** (e.g. the `summary-merge` step in the booking summary).
- **Business milestones** (e.g. “order validated”, “payment committed”).
- **Non-instrumented or legacy calls** (e.g. a custom client that does not create spans).
- **Async or background work** where you want a dedicated span.

HTTP calls made via WebClient are already traced; you do not need custom spans for them unless you want a different or additional span name.

---

## 7. Questions and answers

### Q: If we add JDBC dependencies, will all DB calls be traced automatically for all databases?

**A:** No. Adding only JDBC dependencies (e.g. `spring-boot-starter-jdbc` or a DB driver) does **not** make Spring Boot + Micrometer Tracing (Brave) create spans for database calls. HTTP and WebClient are auto-instrumented; JDBC is not by default.

To get DB calls traced you need one of:

- **OpenTelemetry Java Agent**: run with `-javaagent:opentelemetry-javaagent.jar`; it auto-instruments many JDBC drivers (MySQL, Postgres, Oracle, H2, etc.) and can export to Zipkin (e.g. via OTLP).
- **Brave JDBC instrumentation**: add Brave’s JDBC modules and wrap your `DataSource` so each statement/connection gets a span (support varies by driver).
- **Spring Boot 3.2+ DataSource observation**: with Micrometer Tracing present, Spring’s observed `DataSource` can produce spans for calls through that DataSource.

So “all DB” is only true if your chosen instrumentation supports your drivers; the OTel agent covers the broadest set.

---

### Q: What does the Zipkin server actually store for each request?

**A:** For each request, Zipkin stores:

- **One trace** (trace ID).
- **All spans** for that trace, each with: trace ID, span ID, parent span ID, name, start time, duration, tags, and service (endpoint) information.

It does **not** store request/response bodies, full headers, or application logs unless you add them as span tags or events.

---

### Q: Is Zipkin hierarchical?

**A:** Yes. A trace is a **tree of spans**. Each span can have a parent span ID. The Zipkin UI shows the trace as a hierarchy (and/or timeline). Example: root = gateway request; children = `summary-merge`, GET booking, GET cargo, GET payment.

---

### Q: The “GET booking” call is internal (gateway → booking service as part of the summary request). Does it still create a span automatically?

**A:** Yes. That call is an **outbound HTTP request** from the gateway. The WebClient/Brave integration does not distinguish “internal” vs “external”; it creates a **child span** for every outbound HTTP call and propagates the trace ID (and parent span ID) so the span appears under the same trace. So the booking (and cargo, payment) calls get spans automatically.

---

### Q: Where in the Sea Route gateway should we add custom spans?

**A:** The main place implemented is **BookingSummaryHandler**: a custom span **`summary-merge`** wraps the in-memory step that merges booking, cargo, and invoice into `BookingSummaryResponse`. The three backend HTTP calls are already traced by WebClient; the custom span makes the merge step visible. For other routes that only forward to a backend, the default gateway + backend spans are usually enough unless you want extra business-named spans.

---

## 8. Running and testing

1. **Zipkin**: e.g. `docker run -d -p 9411:9411 openzipkin/zipkin`. Then open the UI (e.g. http://localhost:9411), search by service name (e.g. `sea-route-gateway`), and inspect traces and spans.
2. **Prometheus**: Configure Prometheus to scrape `http://<gateway>:8080/actuator/prometheus`, then run Prometheus and optionally Grafana for dashboards and alerts.
3. Send traffic through the gateway (e.g. `GET /api/v1/bookings/{id}/summary` with a valid JWT). In Zipkin you should see one trace per request with the gateway span, `summary-merge`, and HTTP client spans for booking, cargo, and payment. In Prometheus you should see `booking_summary_calls_total` and `booking_summary_duration_*` (and other metrics).

---

## 9. Reference

- **FLOW.md** (Step 8): Short overview of observability in the gateway flow.
- **application.yml**: `management.*` and tracing/Zipkin settings.
- **BookingSummaryHandler**: Custom metrics (MeterRegistry) and custom span (Tracer, `summary-merge`).
