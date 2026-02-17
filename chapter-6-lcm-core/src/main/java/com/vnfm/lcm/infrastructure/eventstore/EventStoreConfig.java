package com.vnfm.lcm.infrastructure.eventstore;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * REVISION – JPA repository and transaction configuration
 * -------------------------------------------------------
 * @Configuration: This class defines beans / config (here, enabling repos and transactions).
 *
 * @EnableJpaRepositories(basePackages = "..."): Tells Spring Data JPA where to look for
 *   interfaces extending JpaRepository. Spring creates a proxy implementation for each
 *   (e.g. EventEntityRepository, SnapshotEntityRepository) and registers them as beans.
 *   Without this, our repository interfaces would not get an implementation.
 *
 * @EnableTransactionManagement: Enables @Transactional. When you call a @Transactional
 *   method, Spring starts a transaction (before the method) and commits or rolls back
 *   (after the method). Required for repository save()/queries to run in a transaction.
 *   (Spring Boot often enables this by default, but here we make it explicit.)
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.vnfm.lcm.infrastructure.eventstore", "com.vnfm.lcm.infrastructure.outbox", "com.vnfm.lcm.infrastructure.idempotency", "com.vnfm.lcm.infrastructure.saga", "com.vnfm.lcm.infrastructure.readside"})
@EnableTransactionManagement
public class EventStoreConfig {
}
