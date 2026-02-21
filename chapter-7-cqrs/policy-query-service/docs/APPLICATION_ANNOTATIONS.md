# Policy Query Application – Annotations Explained

This document explains the annotations used on `PolicyQueryApplication` and the concepts behind them.

---

## 1. @EnableConfigurationProperties(PolicyQueryProperties.class)

### What it does

- **Registers** `PolicyQueryProperties` as a Spring bean so you can inject it in any component (e.g. controllers, services, Kafka consumer).
- **Binds** all configuration under the prefix `policy-query` (from `application.yml` or `application-*.yml`) into that class. Nested keys like `policy-query.kafka.topic` map to getters/setters and nested objects (e.g. `PolicyQueryProperties.Kafka`).

### Why use a properties class instead of @Value?

**Reason 1: Nested / grouped config**

With `@Value` you would repeat long property keys in every class that needs them:

```java
@Value("${policy-query.kafka.topic}") private String kafkaTopic;
@Value("${policy-query.kafka.dlq-topic}") private String dlqTopic;
@Value("${policy-query.elasticsearch.index-prefix}") private String indexPrefix;
@Value("${policy-query.pagination.default-size}") private int defaultSize;
@Value("${policy-query.pagination.max-size}") private int maxSize;
```

- Property names are string literals in many places → easy to typo, hard to refactor.
- There is no single place that defines “all policy-query config” or sensible defaults.

With **@ConfigurationProperties**, one class maps the entire `policy-query.*` tree. You inject `PolicyQueryProperties` and use `getKafka().getTopic()`, `getPagination().getDefaultSize()`, etc. No repeated keys, and defaults live in one place.

**Reason 2: @Value does not bind nested objects**

`@Value` is for a **single** property per field. It does **not** bind a whole section of YAML into an object.

You can use `@Value` for each leaf (as above), but you **cannot** do:

```java
@Value("???")
private Kafka kafka;  // no way to bind policy-query.kafka to an object with @Value
```

For structured config like `policy-query.kafka`, `policy-query.elasticsearch`, `policy-query.pagination`, you need **@ConfigurationProperties** (or manual `Environment` + parsing). So for this kind of config, **you cannot use @Value in a clean way** — you would be forced to many separate `@Value` strings and lose structure and type safety.

### Summary

| Approach | Use when |
|----------|----------|
| **@ConfigurationProperties** | Multiple related properties, nested config, one place for defaults and validation (e.g. `policy-query.*`). |
| **@Value** | One-off, simple values (e.g. `server.port`, a single feature flag). |

---

## 2. @EnableRetry

### What it does

- **Enables** Spring Retry for the application.
- Methods annotated with **@Retryable** are retried when they throw a specified exception (e.g. a transient failure). You can set max attempts, delay between attempts, and backoff.
- Optionally, a method annotated with **@Recover** can run when all retries for a given exception type have failed (e.g. send to DLQ or log).

### Example

```java
@Service
public class SomeService {

    @Retryable(
        value = { TransientDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public void syncToElasticsearch(PolicyEvent event) {
        // If this throws TransientDataAccessException, Spring retries up to 3 times
        // with 1 second delay between attempts
        elasticsearchClient.index(event);
    }

    @Recover
    public void recover(TransientDataAccessException e, PolicyEvent event) {
        // Called after all 3 attempts failed – e.g. send to DLQ or log
        log.error("Sync failed after retries for event {}", event.getId(), e);
    }
}
```

### Why use it

Useful for **transient failures** (e.g. database or Elasticsearch temporarily unavailable, network blip). A few automatic retries often make the call succeed without changing business logic.

### Important

**Without @EnableRetry**, the framework does not process @Retryable or @Recover; those annotations have no effect. Adding **@EnableRetry** on the application class (or a @Configuration class) turns on this behaviour for the whole application.

---

## 3. How @KafkaListener gets the topic (SpEL vs placeholder)

We want the listener topic to come from **PolicyQueryProperties** so config is single-sourced. Annotation attributes cannot call Java methods directly, but **Spring supports SpEL** in `@KafkaListener` attributes.

So in **PolicyEventConsumer** we use a **SpEL expression** that references the properties bean:

```java
@KafkaListener(
    topics = "#{@policyQueryProperties.kafka.topic}",
    groupId = "${spring.kafka.consumer.group-id:policy-query-service}",
    ...
)
```

- **`#{@policyQueryProperties.kafka.topic}`** – SpEL: evaluate at startup by calling the **policyQueryProperties** bean’s `getKafka().getTopic()`. Same value as `properties.getKafka().getTopic()` used in code (e.g. we already use the bean for `sendToDlq`).
- **groupId** stays as **`${...}`** because it comes from **spring.kafka.consumer.group-id**, which is not on **PolicyQueryProperties**.

So we **do** use the properties class “via a variable” in the sense that the topic is **sourced from the bean** via SpEL, not from a separate placeholder. Default topic value is still defined once in **PolicyQueryProperties.Kafka** (`private String topic = "policy-events"`).

---

## Reference

- **PolicyQueryProperties**: `src/main/java/com/cqrs/policyquery/config/PolicyQueryProperties.java`
- **PolicyQueryApplication**: `src/main/java/com/cqrs/policyquery/PolicyQueryApplication.java`
- **Application config**: `src/main/resources/application.yml` (under `policy-query.*`)
