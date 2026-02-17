package com.telecom.nfvo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NFVO = Network Function Virtualisation Orchestrator.
 *
 * CONCEPT (framework-agnostic): In a telecom/5G microservices architecture, the NFVO
 * is the "orchestrator" that coordinates multiple VNFs. It uses a logical service name
 * (e.g. "vnfm-service") and relies on the platform (K8s, Compose) for Service Discovery—
 * no hardcoded IPs.
 * ------------------------------------------------------------------------------------
 * SPRING ANNOTATIONS USED:
 * ------------------------------------------------------------------------------------
 * @SpringBootApplication
 *   - Meta-annotation that combines three annotations:
 *     1. @Configuration         → This class can define @Bean methods.
 *     2. @EnableAutoConfiguration → Spring Boot auto-configures beans based on classpath
 *        (e.g. if WebFlux is on classpath, it sets up reactive web server and WebClient).
 *     3. @ComponentScan         → Scans this package and all sub-packages for
 *        @Component, @Service, @Controller, @RestController, @Configuration, etc.,
 *        and registers them as beans.
 *   - Effect: One annotation starts the app and wires the typical Spring Boot stack.
 *   - In other frameworks: Quarkus uses @QuarkusMain or just a main class; Micronaut
 *     uses @MicronautApplication; in plain Java you would manually create and run an
 *     embedded server (e.g. Tomcat/Jetty) and register controllers.
 * ------------------------------------------------------------------------------------
 * CLASS: NfvoApplication
 *   - Entry point. Must have a public static void main(String[] args).
 *   - SpringApplication.run(...) starts the embedded server and the application context.
 * ------------------------------------------------------------------------------------
 * org.springframework.boot.SpringApplication
 *   - Spring Boot class (not JVM built-in). Static run(Class<?> primarySource, String[] args)
 *   loads configuration, creates ApplicationContext, starts embedded server (e.g. Netty
 *   for WebFlux or Tomcat for MVC). In other frameworks: Quarkus runs from io.quarkus.runner;
 *   Micronaut has ApplicationContext.run().
 */
@SpringBootApplication
public class NfvoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NfvoApplication.class, args);
    }
}
