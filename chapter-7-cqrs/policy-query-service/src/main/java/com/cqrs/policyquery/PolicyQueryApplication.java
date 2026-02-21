package com.cqrs.policyquery;

import com.cqrs.policyquery.config.PolicyQueryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Policy Query Service – CQRS read-side for policy history (e.g. Elasticsearch + Kafka).
 *
 * <h3>@EnableConfigurationProperties(PolicyQueryProperties.class)</h3>
 * <ul>
 *   <li>Registers {@link PolicyQueryProperties} as a Spring bean so it can be injected elsewhere.</li>
 *   <li>Binds all {@code policy-query.*} keys from application.yml to that class (type-safe, nested).</li>
 *   <li>Why not @Value? (1) @Value forces repeated string keys and no nested objects; (2) a properties
 *       class gives one place for defaults, validation, and refactoring. See docs/APPLICATION_ANNOTATIONS.md.</li>
 * </ul>
 *
 * <h3>@EnableRetry</h3>
 * <ul>
 *   <li>Enables Spring Retry so methods annotated with @Retryable are retried on failure (e.g. transient
 *       Elasticsearch/DB errors). Optional @Recover runs after all retries fail.</li>
 *   <li>Without this annotation, @Retryable and @Recover have no effect.</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(PolicyQueryProperties.class)
@EnableRetry
public class PolicyQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyQueryApplication.class, args);
    }
}
