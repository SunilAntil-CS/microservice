package com.vnfm.lcmcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * REVISION – Spring Boot entry point
 * ---------------------------------
 * @SpringBootApplication is a convenience annotation that combines:
 *   1. @Configuration         – this class can define @Bean methods
 *   2. @EnableAutoConfiguration – Spring Boot configures beans from classpath + application.yml
 *      (e.g. DataSource, EntityManagerFactory, KafkaTemplate when dependencies are present)
 *   3. @ComponentScan        – scans this package and sub-packages for @Component, @Service,
 *      @Repository, @Controller, @RestController; registers them as beans
 *
 * SpringApplication.run() creates the ApplicationContext, loads all beans, starts embedded
 * server (e.g. Tomcat), and runs the app. Without Spring Boot you would wire XML or Java config manually.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.vnfm.lcmcore", "com.vnfm.lcm"})
@EntityScan(basePackages = {"com.vnfm.lcm.infrastructure.eventstore", "com.vnfm.lcm.infrastructure.outbox", "com.vnfm.lcm.infrastructure.idempotency", "com.vnfm.lcm.infrastructure.saga", "com.vnfm.lcm.infrastructure.readside"})
@EnableScheduling
public class LcmCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LcmCoreApplication.class, args);
    }
}
