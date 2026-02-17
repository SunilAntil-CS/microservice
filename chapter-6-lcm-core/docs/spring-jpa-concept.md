# Spring and JPA – Comprehensive Notes

## Table of Contents
1. [Spring Framework Overview](#spring-framework-overview)
2. [Inversion of Control (IoC) and Dependency Injection](#inversion-of-control-ioc-and-dependency-injection)
   - [Bean Scopes](#bean-scopes)
   - [Configuration Methods](#configuration-methods)
   - [Types of Dependency Injection](#types-of-dependency-injection)
   - [Autowiring Modes](#autowiring-modes)
   - [Qualifiers and @Primary](#qualifiers-and-primary)
3. [Spring Stereotype Annotations](#spring-stereotype-annotations)
   - [@Component](#component)
   - [@Repository](#repository)
   - [@Service](#service)
   - [@Controller / @RestController](#controller--restcontroller)
4. [Spring Data JPA](#spring-data-jpa)
   - [ORM and JPA Basics](#orm-and-jpa-basics)
   - [Entity Mapping](#entity-mapping)
   - [JPA Repositories](#jpa-repositories)
   - [Custom Queries](#custom-queries)
5. [Transactions in Spring](#transactions-in-spring)
6. [Database Configuration](#database-configuration)
7. [Testing with @DataJpaTest](#testing-with-datajpatest)
8. [Best Practices Summary](#best-practices-summary)
9. [Transactional Outbox Pattern (Study Notes)](#transactional-outbox-pattern-study-notes)

---

## Spring Framework Overview
Spring is a comprehensive framework for enterprise Java development. Its core features include:
- **Inversion of Control (IoC)** container for managing object lifecycles and dependencies.
- **Aspect-Oriented Programming (AOP)** for cross-cutting concerns.
- **Spring MVC** for web applications.
- **Spring Data** for simplified data access.
- **Spring Boot** for auto-configuration and rapid development.

---

## Inversion of Control (IoC) and Dependency Injection

IoC means the framework controls the flow of the program and object creation. Dependency Injection (DI) is the pattern used to implement IoC: objects receive their dependencies from an external source (the container) rather than creating them internally.

### Bean Scopes
- **singleton** (default) – one instance per Spring container.
- **prototype** – new instance each time requested.
- **request** – one instance per HTTP request (web-aware).
- **session** – one instance per HTTP session.
- **application** – scoped to ServletContext.

### Configuration Methods
- **XML configuration** (legacy).
- **Java-based configuration** using `@Configuration` and `@Bean`.
- **Annotation-based configuration** using `@Component` and its specializations.

### Types of Dependency Injection
1. **Field Injection** (not recommended)
   ```java
   @Autowired
   private MyService myService;
   ```
   - Fields cannot be `final`.
   - Harder to test (requires reflection or Spring).
   - Dependencies hidden.

2. **Setter Injection**
   ```java
   @Autowired
   public void setMyService(MyService myService) { ... }
   ```
   - Allows optional dependencies (setter can be called later).
   - Still not ideal for mandatory dependencies.

3. **Constructor Injection (preferred)**
   ```java
   private final MyService myService;
   
   public MyClass(MyService myService) {
       this.myService = myService;
   }
   ```
   - Fields can be `final` → immutability.
   - All dependencies mandatory and clear.
   - Easy to test with plain `new`.
   - Spring automatically uses the constructor if it’s the only one (no `@Autowired` needed from Spring 4.3+).

### Autowiring Modes
- **byType** (default): injects bean of matching type; fails if zero or multiple candidates.
- **byName**: matches bean name with property name.
- **constructor**: similar to byType but for constructor arguments.

### Qualifiers and @Primary
- **@Primary** – marks a bean as the primary candidate when multiple beans of same type exist.
- **@Qualifier** – used to specify exactly which bean to inject by name.

```java
@Bean
@Primary
public DataSource dataSource() { ... }

@Bean
@Qualifier("backup")
public DataSource backupDataSource() { ... }

// Injection
@Autowired
@Qualifier("backup")
private DataSource dataSource;
```

---

## Spring Stereotype Annotations

All are meta-annotated with `@Component`, so they are auto-detected during component scanning.

| Annotation | Purpose | Special Behavior |
|------------|---------|------------------|
| `@Component` | Generic stereotype for any Spring-managed bean. | – |
| `@Repository` | Data access layer (DAO). | Translates persistence exceptions (e.g., `SQLException`) into Spring’s `DataAccessException` hierarchy. |
| `@Service` | Service layer (business logic). | – (just a marker). |
| `@Controller` | Web layer (MVC controller). | Often used with `@RequestMapping`; returns view names. |
| `@RestController` | Combines `@Controller` and `@ResponseBody`. | All handler methods return domain objects (JSON/XML) directly. |

---

## Spring Data JPA

Spring Data JPA simplifies data access by providing repository abstractions built on JPA (Java Persistence API).

### ORM and JPA Basics
- **Object-Relational Mapping** – maps Java objects to database tables.
- **Entity** – a class annotated with `@Entity`, representing a table.
- **Persistence Context** – manages entities and their lifecycles.
- **EntityManager** – the primary JPA interface for CRUD and queries.

### Entity Mapping

```java
@Entity
@Table(name = "events")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    private String eventData;

    // ... getters, setters, constructors
}
```

**Common annotations:**
- `@Entity` – marks a class as a JPA entity.
- `@Table` – specifies table name (optional if matches class name).
- `@Id` – marks the primary key field.
- `@GeneratedValue` – defines primary key generation strategy (e.g., `AUTO`, `IDENTITY`, `UUID`).
- `@Column` – maps a field to a column; allows specifying name, nullable, length, etc.
- `@Transient` – marks a field not to be persisted.
- `@Enumerated` – stores an enum as string or ordinal.
- `@Lob` – large object.
- Relationships: `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`, with `@JoinColumn`.

### JPA Repositories

Spring Data JPA provides repository interfaces with built-in CRUD methods.

```java
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    List<EventEntity> findAllByAggregateIdOrderByVersionAsc(UUID aggregateId);
}
```

**Key interfaces:**
- `CrudRepository<T, ID>` – basic CRUD methods.
- `PagingAndSortingRepository<T, ID>` – adds pagination and sorting.
- `JpaRepository<T, ID>` – extends above, adds JPA-specific methods like `flush()` and batch operations.

You can define custom query methods by following naming conventions (`findBy...`, `countBy...`, etc.) or using `@Query`.

### Custom Queries

```java
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    
    // Derived query method
    List<EventEntity> findByAggregateIdOrderByVersionAsc(UUID aggregateId);
    
    // JPQL query
    @Query("SELECT e FROM EventEntity e WHERE e.aggregateId = :aggId ORDER BY e.version ASC")
    List<EventEntity> findEventsForAggregate(@Param("aggId") UUID aggregateId);
    
    // Native SQL query
    @Query(value = "SELECT * FROM events WHERE aggregate_id = :aggId ORDER BY version ASC", nativeQuery = true)
    List<EventEntity> findEventsNative(@Param("aggId") UUID aggregateId);
}
```

---

## Transactions in Spring

The `@Transactional` annotation declares that a method or class should be executed within a transaction.

```java
@Service
public class VnfService {
    @Autowired
    private EventRepository eventRepository;

    @Transactional
    public void createVnf(...) {
        // multiple repository calls – all succeed or roll back together
    }
}
```

**Key attributes:**
- `propagation` – defines transaction boundaries (e.g., `REQUIRED`, `REQUIRES_NEW`).
- `isolation` – isolation level (e.g., `READ_COMMITTED`).
- `timeout` – transaction timeout in seconds.
- `readOnly` – hint for read-only transactions (optimizations).
- `rollbackFor` / `noRollbackFor` – specify which exceptions cause rollback.

**Note:** By default, rollback happens only for unchecked exceptions (`RuntimeException` and `Error`). Checked exceptions do not trigger rollback unless specified with `rollbackFor`.

---

## Database Configuration

In a Spring Boot application, database settings are typically placed in `application.properties` or `application.yml`.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vnfm_db
    username: vnfm
    password: vnfm123
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate   # none, validate, update, create, create-drop
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

**Connection pooling:** Spring Boot uses HikariCP by default; you can tune it via `spring.datasource.hikari.*` properties.

---

## Testing with @DataJpaTest

`@DataJpaTest` is a Spring Boot test slice that focuses only on JPA components. It:

- Configures an in-memory database (e.g., H2) by default.
- Scans `@Entity` classes and Spring Data JPA repositories.
- Disables full auto-configuration.
- Provides `@Transactional` (rollback after each test).

```java
@DataJpaTest
class EventRepositoryTest {
    @Autowired
    private EventRepository eventRepository;

    @Test
    void testSaveAndFind() {
        EventEntity event = new EventEntity(...);
        eventRepository.save(event);
        List<EventEntity> found = eventRepository.findAllByAggregateIdOrderByVersionAsc(someId);
        assertThat(found).hasSize(1);
    }
}
```

You can replace the default H2 with a real database using `@AutoConfigureTestDatabase(replace = NONE)`.

---

## Best Practices Summary

- **Use constructor injection** for mandatory dependencies; prefer `private final` fields.
- **Program to interfaces** (Dependency Inversion Principle) – define repository interfaces and inject them.
- **Mark data access classes with `@Repository`** to get persistence exception translation.
- **Use `@Transactional` at the service layer**, not the repository layer.
- **Design entities with JPA-friendly mappings**, and use `@Column(nullable = false)` for required columns.
- **For composite unique constraints**, use `@Table(uniqueConstraints = @UniqueConstraint(...))`.
- **Prefer derived query methods** for simple queries; use `@Query` for complex ones.
- **Use `@DataJpaTest` for repository unit tests** to avoid loading the whole context.
- **Externalize database configuration** in `application.yml` and use profiles for different environments.
- **Be consistent with primary keys** – use surrogate keys (e.g., `UUID` or `Long`) and enforce business rules with unique constraints.

---

## Transactional Outbox Pattern (Study Notes)

This section explains how the **outbox pattern** is implemented in this project using JPA, Spring Data, and scheduling. It ties together entities, repositories, transactions, and Kafka.

### What problem does the outbox solve?

When you need to **send a message to Kafka (or another system) and also write to the database**, doing them in two steps is risky:

- If you **commit the DB first** and then send to Kafka, a crash in between loses the message.
- If you **send to Kafka first** and then write to the DB, the DB write might fail and Kafka already has a message that is never reflected in your data.

**Idea:** Write the outgoing message **into a table (the “outbox”) in the same transaction** as your business write. So one transaction = business row(s) + outbox row. Then a **separate process** (scheduled job) reads from the outbox and publishes to Kafka. If that process fails, the message is still in the DB and can be retried. No message is lost once the transaction commits.

### Outbox table and entity (JPA)

The **outbox** table stores one row per message to publish. In this project the entity is **`OutboxMessage`** with columns:

| Column (DB)   | Entity field   | Purpose |
|---------------|----------------|---------|
| `id`          | `id`           | Primary key (e.g. identity). |
| `message_id`  | `messageId`    | Unique id for the message (e.g. UUID); used for idempotency at consumer. |
| `destination` | `destination`  | Logical destination (e.g. `"vim.manager"`); mapped to Kafka topic by the publisher. |
| `message_type` | `messageType` | Type of message (e.g. `"InstantiateVnfCommand"`) for consumer. |
| `payload`     | `payload`      | JSON string (the actual message body). Stored as `@Lob` / text. |
| `status`      | `status`       | Enum: `PENDING` (waiting to be sent) or `SENT` (successfully published). |
| `retry_count` | `retryCount`   | Number of publish attempts so far (0 = first try). |
| `created_at`  | `createdAt`    | When the row was inserted. |
| `next_retry_at` | `nextRetryAt` | When to try (or retry) publishing; used for exponential backoff. |
| `last_error`  | `lastError`    | Last failure message (for debugging). |

**Study note – JPA details:**

- **`@Enumerated(EnumType.STRING)`** for `status`: stores `"PENDING"` / `"SENT"` in the DB instead of ordinal (0, 1), so the schema is robust if you reorder enum values.
- **Index on `(status, next_retry_at)`**: the forwarder query is “all PENDING messages where `next_retry_at <= now()`”. The index makes this query fast.
- **`payload` as String**: keeps the entity transport-agnostic; the actual JSON is produced by the application when writing the row.

### OutboxRepository (Spring Data JPA)

The repository extends **`JpaRepository<OutboxMessage, Long>`** and adds one derived query:

```java
List<OutboxMessage> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
    OutboxStatus status, Instant now);
```

**Study note:** Spring Data turns the method name into a query: “find by status and nextRetryAt less than or equal to `now`, order by nextRetryAt ascending”. This is exactly what the forwarder needs: due messages, oldest first.

### MessagePublisher (port) and KafkaMessagePublisher (adapter)

- **`MessagePublisher`** is an interface with `void publish(OutboxMessage message)`. It is the “port” so that the forwarder does not depend on Kafka directly; we can test with a mock and swap implementations.
- **`KafkaMessagePublisher`** is the adapter: it maps `destination` to a Kafka topic (e.g. `vim.manager` → `vim.commands`), then uses **`KafkaTemplate<String, String>`** to send the `messageId` as key and the `payload` as value. Kafka producer config (bootstrap servers, key/value serializers) comes from **`application.yml`** under `spring.kafka.producer`.

### OutboxForwarder (scheduled component)

The **OutboxForwarder** is a **`@Component`** with a method annotated **`@Scheduled(fixedDelayString = "${lcm.outbox.forwarder.fixed-delay:5000}")`** (every 5 seconds by default). In that method it:

1. Calls **`outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(PENDING, now)`** to load due messages.
2. For each message, calls **`messagePublisher.publish(message)`**.
3. **On success:** sets `message.setStatus(SENT)`, clears `lastError`, and **`outboxRepository.save(message)`**.
4. **On failure (PublishException):** increments `retryCount`, sets `lastError`, sets **`nextRetryAt = now + baseDelay * 2^retryCount`** (exponential backoff), and saves. The message stays `PENDING` and will be picked up on a later run.

**Study notes:**

- **`@EnableScheduling`** must be enabled (e.g. on the main `@SpringBootApplication` class) for `@Scheduled` to run.
- **`@Transactional`** on the forward method ensures that the updates (status, retry fields) are committed in a single transaction.
- **Exponential backoff** avoids hammering Kafka when the broker is down: first retry after 2s, then 4s, 8s, etc.

### Configuration (Kafka producer)

In **`application.yml`**:

- **`spring.kafka.bootstrap-servers`**: broker address (e.g. `localhost:9092`).
- **`spring.kafka.producer.key-serializer`** / **`value-serializer`**: typically **`StringSerializer`** when sending messageId and JSON payload.
- **`lcm.outbox.forwarder.fixed-delay`**: interval in milliseconds between the end of one forward run and the start of the next (e.g. `5000` = 5 seconds).

### Testing the forwarder

**OutboxForwarderTest** uses **Mockito**: it mocks **`OutboxRepository`** and **`MessagePublisher`** and injects them into **OutboxForwarder**. Tests verify:

- When there are no due messages, `publish` is never called.
- When publish succeeds, the message status is set to `SENT` and `save` is called.
- When publish throws **`PublishException`**, `retryCount` is incremented, `nextRetryAt` and `lastError` are set, and `save` is called.

No real DB or Kafka is needed; the behaviour of the forwarder is tested in isolation.

### Summary (for exams / revision)

- **Outbox table** = same-DB queue for messages to publish; written in the **same transaction** as the business write.
- **OutboxMessage** = JPA entity; **OutboxRepository** = Spring Data JPA with a derived query for PENDING + due messages.
- **MessagePublisher** = port; **KafkaMessagePublisher** = adapter that maps destination → topic and uses **KafkaTemplate**.
- **OutboxForwarder** = **`@Scheduled`** job that loads due messages, publishes them, and updates status (SENT) or retry fields (exponential backoff) on failure.
- **Retries** = on failure, message stays PENDING; **nextRetryAt** and **retryCount** implement exponential backoff so the same message is retried later without losing it.

---
