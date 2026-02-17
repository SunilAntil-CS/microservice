# Spring & JPA – Questions and Answers

This document collects common questions and answers from the chapter-6-lcm-core project, useful for revision and interviews.

---

## Spring Boot

### Q: What does “@EnableAutoConfiguration configures beans from classpath + application.yml” mean?

**A:**  
@EnableAutoConfiguration (inside @SpringBootApplication) tells Spring Boot to look at your **classpath** (which JARs you have) and **application.yml** (property values), then create and configure the right beans automatically.

- **From classpath:** If it finds e.g. `spring-boot-starter-data-jpa` and the PostgreSQL driver, it creates a **DataSource** and **EntityManagerFactory**. If it finds `spring-kafka`, it creates **KafkaTemplate** and consumer config. So *which* beans are created depends on your dependencies.
- **+ application.yml:** *How* those beans are configured comes from properties: e.g. `spring.datasource.url`, `spring.jpa.hibernate.ddl-auto`, `spring.kafka.bootstrap-servers`. No (or less) Java config needed.

So: “configures beans” = Spring Boot creates and wires beans; “from classpath” = which tech (JPA, Kafka, etc.); “+ application.yml” = the values (URLs, ports, etc.) for those beans.

---

### Q: Why do we have two config files: application.yml and application-local.yml?

**A:**  
They target **different ways of running** the app, using Spring Boot **profiles**.

- **application.yml** – Loaded by default. Configured for the full setup: **PostgreSQL** and **Kafka** (e.g. when you run with Docker).
- **application-local.yml** – Loaded only when the **`local`** profile is active (`-Dspring-boot.run.profiles=local`). Uses **H2 in-memory** and **disables Kafka** so you can run without Docker.

So: one file = one environment; two files = default (Postgres + Kafka) vs local (H2, no Kafka). Profiles are the mechanism: `application-{profile}.yml` is loaded when that profile is active.

---

### Q: Why not put @EnableTransactionManagement in the main Spring Boot application class?

**A:**  
You **can** put it there. It’s a global setting; it doesn’t matter which @Configuration class (or the main class) declares it.

It’s in EventStoreConfig only to **group** it with @EnableJpaRepositories in one “infrastructure” config. Also, **Spring Boot enables transaction management by default** when you have spring-boot-starter-data-jpa (or -jdbc), so the annotation is often redundant.

---

### Q: What is the difference between @ComponentScan and @EntityScan?

**A:**  

| | **@ComponentScan** | **@EntityScan** |
|---|-------------------|------------------|
| **Purpose** | Where to find **Spring beans** (@Component, @Service, @Repository, @Controller) | Where to find **JPA @Entity** classes (ORM mapping) |
| **Used by** | Spring’s context (dependency injection, wiring) | JPA / Hibernate (building the persistence metamodel, table mapping) |
| **Result** | Those classes become **beans** in the application context | Those classes are **mapped to DB tables** (metadata for persistence) |

So: ComponentScan = “where to find **application/bean classes**”; EntityScan = “where to find **ORM entity classes**.” They’re separate because entities are not necessarily beans; they’re metadata for the persistence layer.

---

### Q: Why do we create beans in a separate package (com.vnfm.lcm) instead of com.vnfm.lcmcore?

**A:**  
For **structure and boundaries**, not because of “entity vs bean.”

- **com.vnfm.lcmcore** – The **application** that runs (main class, “run this Spring Boot app”).
- **com.vnfm.lcm** – The **LCM domain + infrastructure** (domain model, event store, repositories). So “the LCM module” lives under `com.vnfm.lcm`, and “the app that runs it” lives under `com.vnfm.lcmcore`. Putting beans in `com.vnfm.lcm` keeps the LCM logic in one place and allows another app to depend on that module without pulling in the whole lcmcore app.

---

## JPA & Entities

### Q: “Entities are not beans” – what does that mean?

**A:**  
- **Bean** = Something Spring **creates and holds** in the application context; you get it via @Autowired (e.g. EventEntityRepository, JdbcEventStore).
- **Entity** = A **class** that JPA uses to map objects to a DB table (e.g. EventEntity with @Entity). Spring does **not** create a bean of type EventEntity. You create instances yourself with `new EventEntity(...)` and pass them to the repository (e.g. `eventRepository.save(entity)`). So: the **repository** is the bean; the **entity** is the “shape” of the data. The entity class is **metadata for persistence** (table name, columns, PK); it is not registered as a bean.

---

### Q: What is “metadata for persistence”?

**A:**  
**Metadata** = “data that describes something else.” For an entity class, JPA uses it to know:

- **Which table:** e.g. @Table(name = "events")
- **Which columns:** e.g. @Column(name = "aggregate_id")
- **Primary key:** @Id, @GeneratedValue

So the entity class is a “blueprint” that tells JPA how to turn a Java object into SQL (INSERT/UPDATE) and a DB row into a Java object. That blueprint is the metadata for persistence. JPA needs to **see** the entity class (@EntityScan), but the entity does not need to be a Spring bean.

---

### Q: What does @Table(name = "events", indexes = { @Index(...) }) mean?

**A:**  
- **name = "events"** – This entity is mapped to the table named **events** in the database.
- **indexes = { @Index(...) }** – Declares an index on that table:
  - **name** – Logical name of the index (e.g. idx_events_aggregate_version).
  - **columnList = "aggregate_id, version"** – The index is on the columns **aggregate_id** and **version** (in that order). It speeds up queries that filter by aggregate_id and use version (e.g. “all events for aggregate a1 ordered by version” or “max version for a1”), because the DB can use the index instead of scanning the whole table.

---

### Q: How does the index on (aggregate_id, version) help find the maximum version for an aggregate?

**A:**  
The index is stored **sorted**: first by aggregate_id, then by version. So for aggregate a1 you have (a1,1), (a1,2), …, (a1,10) in order. To get **max version for a1**:

1. The DB uses the index to jump to the “a1” range (no full table scan).
2. Within that range, version is already sorted, so the **maximum version** is the **last** entry (e.g. (a1, 10) → 10). So the index both narrows the search to one aggregate and makes “max version” a simple “last value in that slice.”

---

### Q: What does @GeneratedValue(strategy = GenerationType.IDENTITY) mean?

**A:**  
“The **database** generates the primary key when the row is inserted; the application does **not** set it.” So the `id` column is an **identity / auto-increment** column: the DB assigns the next number (1, 2, 3, …) on each INSERT. You don’t call `entity.setId(...)`; after save, the DB has filled in `id`.

---

### Q: What does “use the database’s identity / auto-increment column” mean?

**A:**  
At the DB level, the column is defined so that when you INSERT a row **without** providing a value for that column, the database automatically uses the **next** value (e.g. last was 5 → next is 6). So “identity” or “auto-increment” = the DB generates the next number for that column on each insert. Different DBs use different syntax (e.g. PostgreSQL SERIAL/IDENTITY, MySQL AUTO_INCREMENT, H2 AUTO_INCREMENT).

---

### Q: How does the database implement auto-increment? Does it scan all IDs?

**A:**  
No. The database keeps a **sequence** (counter) for that column. On insert it: (1) reads the current value (e.g. 100), (2) increments it (101), (3) uses 101 for the new row, (4) stores the new counter value. So the next ID comes from this small counter, not from scanning the table. That’s fast and avoids two inserts getting the same “next” ID.

---

### Q: Is the sequence created for each id column or for each table?

**A:**  
**Per column.** Each identity/auto-increment column has its own sequence or counter. So table `events` has one sequence for its `id` column, and table `snapshots` has a **different** sequence for its `id` column. They don’t share a counter.

---

### Q: Why can’t we use eventId as the primary key (ID) instead of a separate id column?

**A:**  
You **can** use eventId as the primary key. It’s a design choice. Using a separate numeric `id` with IDENTITY is common for: (1) convention (surrogate key per table), (2) smaller key size (BIGINT vs UUID string), (3) some DBs’ behavior with clustered indexes (sequential IDs can be friendlier than random UUIDs). So both are valid; the current design uses a surrogate `id` for those reasons.

---

## Idempotency & Filters

### Q: In IdempotencyFilter, what is the purpose of ContentCachingRequestWrapper and ContentCachingResponseWrapper?

**A:**  
They let the filter **read the request and response bodies without breaking the rest of the pipeline**.

- **Request body**  
  In a normal servlet request, the body is a **single stream**: once the filter (or controller) reads it, it’s consumed and can’t be read again. The filter needs to read the body to get **requestId** (when it’s not in the header), and the **controller** still needs to read the same body for `@RequestBody InstantiateRequest`.  
  **ContentCachingRequestWrapper** wraps the request and **caches** the body as it’s read. So the filter can read it (e.g. to parse `requestId`), and when the controller reads the “stream” later, it gets the cached copy. The body is effectively readable twice.

- **Response body**  
  The filter must **cache the response** (to store it in `processed_requests`) **after** the controller has written it. Normally, what the controller writes goes straight to the client and isn’t stored.  
  **ContentCachingResponseWrapper** wraps the response and **copies** everything written to it into an internal buffer. So: the controller writes to the wrapper; after `chain.doFilter()` returns, the filter calls **getContentAsByteArray()** on the wrapper to get that response body and save it; **copyBodyToResponse()** then sends that content to the real response so the client still receives the full response.

**In short:**  
- **ContentCachingRequestWrapper** – lets the filter read the body (e.g. for requestId) and still leave a readable body for the controller.  
- **ContentCachingResponseWrapper** – lets the filter capture the response body for the idempotency cache and then send it to the client.

Without these wrappers, you’d either consume the request body before the controller could use it, or never see the response body to cache it.

---

### Q: How do we ensure the response we cache was actually produced and accepted by the controller before we proceed to cache it?

**A:**  
Two things guarantee that:

1. **Execution order** – `filterChain.doFilter(requestWrapper, responseWrapper)` is **synchronous**. The filter hands off to the next filter and eventually the controller; only when the controller (and the rest of the chain) finishes does `doFilter` return. So when execution reaches the "cache the response" block, the controller has **already** run and written its response into the wrapper. We are not caching before the controller runs; we run the cache logic **after** the controller has finished.

2. **Only cache 2xx** – The code only caches when `responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300`. So we cache only when the controller returned a success status (e.g. 200 OK, 202 Accepted). If the controller returned 4xx or 5xx, we do **not** cache, so we don't store error responses.

---

### Q: What is OncePerRequestFilter, and is it just the Spring counterpart of the Filter interface?

**A:**  
**OncePerRequestFilter** is not "the Spring version of Filter." It is a **Spring base class that extends the standard `Filter` interface** and adds one guarantee: **your logic runs at most once per logical request**.

With a plain **Filter**, the servlet container can invoke **doFilter() more than once** for a single logical request (e.g. on forward, include, or error dispatch). **OncePerRequestFilter** overrides `doFilter()`, uses a request attribute to remember "this filter already ran," and calls your **doFilterInternal()** only once per request. So your logic runs exactly once even when the container would otherwise call the filter multiple times.

**In short:** Filter = standard contract (can run multiple times per request); OncePerRequestFilter = Filter + "run at most once per request."

---

### Q: Which is better for idempotency: Filter or @ControllerAdvice?

**A:**  
For idempotency (check cache → return cached vs call controller → cache response), the **Filter approach is a better fit** than relying only on @ControllerAdvice.

- **Filter** runs **before** the controller and can **short-circuit**: if the requestId is in the cache, it writes the cached response and does **not** call the controller. It also runs **after** the controller (when `doFilter` returns) so it can capture and cache the response. So both "check request and maybe skip controller" and "cache response after success" fit naturally in one place.

- **@ControllerAdvice** (e.g. ResponseBodyAdvice) runs in the MVC layer **around** the controller. It can inspect or transform the **response** after the controller returns, but it does **not** run "before" the controller in a way that lets you **return a cached response and skip the controller**. So for "check request and short-circuit," you'd need a Filter or interceptor anyway.

**Use Filter for idempotency** when you need to skip the controller on a cache hit. **Use ControllerAdvice** for response-oriented cross-cutting concerns (exception handling, response wrapping, logging).

---

### Q: So is ControllerAdvice only for when we need to check the response, and it doesn't work for checking the request?

**A:**  
Yes, in practice:

- **Request-oriented logic** (e.g. "is this requestId in the cache? If yes, return cached response and **don't call the controller**") – must run **before** the controller. That's what a **Filter** (or **HandlerInterceptor** preHandle) does. **@ControllerAdvice** doesn't give you a "before controller" hook that can short-circuit the controller; by the time advice runs, the controller is already in play.

- **Response-oriented logic** (e.g. "cache this response," "wrap the body in an envelope," "log the response") – fits **@ControllerAdvice** (e.g. ResponseBodyAdvice) well, because it runs when the controller has already produced the response.

So: **check/use the response** → ControllerAdvice works. **Check the request and possibly skip the controller** → you need something that runs on the request before the controller (Filter or interceptor).

---

## Java record vs Lombok

### Q: What is a record in Java, and in which version was it introduced?

**A:**  
A **record** is a kind of class for “a group of fields.” The compiler generates: a constructor taking all components, getters (same name as the component, e.g. `aggregateId()`), and `equals`, `hashCode`, `toString`. Records are immutable (components are final). Introduced as **preview in Java 14**, **finalized in Java 16**.

---

### Q: What is the Lombok annotation similar to a record?

**A:**  
**@Value.** It makes the class immutable and generates constructor, getters (with “get” prefix by default), and equals/hashCode/toString. So it’s the Lombok way to get record-like behavior.

---

### Q: What is the difference between a record and Lombok?

**A:**  
- **Record** – Language feature (Java 16+), no dependency, getters named like the component (e.g. `aggregateId()`), fixed shape (components only), cannot extend a class.
- **Lombok** – Library, requires dependency and often IDE plugin, getters usually `getXxx()`, more flexible (e.g. @Data, @Builder, inheritance). Use Lombok when you need things records don’t support (inheritance, @Builder, mutable beans with @Data).

---

### Q: What does “need things records don’t support (e.g. inheritance, @Builder, @Data for mutable beans)” mean?

**A:**  
- **Inheritance:** Records cannot `extends` another class (only implement interfaces). So if you need e.g. `CustomerDto extends BaseDto`, you use a normal class (and Lombok if you want).
- **@Builder:** Lombok can generate a fluent builder (`Person.builder().name("x").build()`). Records don’t have that built in.
- **@Data for mutable beans:** @Data generates getters and **setters**; the object is mutable. Records are immutable (no setters). So when you need a mutable DTO or bean, you use a class + Lombok @Data, not a record.

---

*Document generated from Q&A in the chapter-6-lcm-core project. Use for revision and interview prep.*
