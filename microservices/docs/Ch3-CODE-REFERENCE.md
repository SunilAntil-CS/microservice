# Chapter 3 – Code reference: annotations, types, and other frameworks

**This repo:** Chapter 3 (Microservices – Sync, Discovery, Async, Reliability). Use this doc as a notes sheet for annotations and types used across the Chapter 3 projects.

This document summarises every Spring annotation, key inbuilt class, and type used in the NFVO/VNFM (and other Ch3) examples, with short equivalents in other frameworks.

---

## Spring Boot / Core

| Annotation / Class | Purpose | Other frameworks (concept) |
|--------------------|--------|----------------------------|
| `@SpringBootApplication` | Entry point: `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. Starts app and scans for beans. | Quarkus: `@QuarkusMain` or main class; Micronaut: `@MicronautApplication`; Node: bootstrap script that loads modules. |
| `@Configuration` | Class is a source of bean definitions; instantiated once. | Quarkus: `@ApplicationScoped`; Micronaut: `@Singleton` or `@Factory`. |
| `@ConfigurationProperties(prefix="x")` | Binds properties `x.*` to this class's fields via setters. | Quarkus: `@ConfigMapping` or `@ConfigProperty`; Micronaut: `@ConfigurationProperties`; Node: ConfigService / env schema. |
| `@Service` | Stereotype `@Component` for "service layer" bean; injected where needed. | Quarkus: `@ApplicationScoped`; Micronaut: `@Singleton`; NestJS: `@Injectable()`. |
| `SpringApplication.run(Class, args)` | Loads config, creates context, starts embedded server. | Quarkus: `Quarkus.run()`; Micronaut: `ApplicationContext.run()`. |

---

## Web (REST)

| Annotation / Class | Purpose | Other frameworks |
|--------------------|--------|------------------|
| `@RestController` | `@Controller` + `@ResponseBody`: return value → response body (e.g. JSON). | JAX-RS: `@Path` on class + `@Produces(APPLICATION_JSON)`; Express: `res.json(...)`. |
| `@RequestMapping("/path")` | Base path for all mappings in the class. | JAX-RS: `@Path("/path")`; Express: `router.use("/path", ...)`. |
| `@GetMapping(value="...", produces=...)` | Maps GET to method; `produces` = response Content-Type. | JAX-RS: `@GET @Path("...") @Produces(...)`; Express: `app.get("...", (req,res)=>...)`. |
| `@PathVariable String id` | Binds path segment `{id}` to method parameter. | JAX-RS: `@PathParam("id")`; Express: `req.params.id`. |
| `MediaType.APPLICATION_JSON_VALUE` | Constant `"application/json"`. | JAX-RS: `MediaType.APPLICATION_JSON`; same idea elsewhere. |

---

## Validation (Jakarta)

| Annotation | Purpose | Other frameworks |
|------------|--------|------------------|
| `@NotBlank` | Field must not be null and not empty after trim. Fail at startup when used with `@ConfigurationProperties`. | Same in Quarkus/Micronaut; Node: class-validator, Joi; Go: validate in config load. |

---

## Resilience4j

| Annotation | Purpose | Other frameworks |
|------------|--------|------------------|
| `@CircuitBreaker(name="x", fallbackMethod="y")` | Wraps method; on failure or when circuit open, calls `y` instead of throwing. Config in `resilience4j.circuitbreaker.instances.x.*`. | Quarkus: SmallRye Fault Tolerance `@CircuitBreaker` + `@Fallback`; Node: opossum or custom; Go: manual or library. |

---

## Reactive (WebFlux / Reactor)

| Type / Class | Purpose | Other frameworks |
|--------------|--------|------------------|
| `WebClient` | Non-blocking HTTP client; `.get().uri(...).retrieve().bodyToMono(T.class)`. | Quarkus: RestClient (reactive); Node: fetch/axios (Promise); Go: net/http or resty. |
| `Mono<T>` | At most one value (or empty/error), async. Like a single-value Future with operators. | RxJava: `Single<T>`; Kotlin: `Flow`; Node: `Promise<T>`. |
| `Duration` (java.time) | Time amount, e.g. `Duration.ofSeconds(2)` for timeout. | JDK 8+; other languages have similar (e.g. Go `time.Second`). |

---

## JSON (Jackson)

| Annotation | Purpose | Other frameworks |
|------------|--------|------------------|
| `@JsonInclude(JsonInclude.Include.NON_NULL)` | Do not serialise fields that are null. | Same in Quarkus/Micronaut; Node: omit in transform or decorator; Go: `json:",omitempty"`. |

---

## Java language / JDK

| Type / Class | Purpose | Notes |
|--------------|--------|--------|
| `record` (Java 16+) | Immutable data carrier; compiler generates ctor, getters, equals, hashCode, toString. | Kotlin data class; Scala case class; Go struct. |
| `Objects.requireNonNull(x, msg)` | Throws NPE if x is null; use in constructors for validation. | JDK; no framework. |
| `Map<K,V>` | Key-value interface. | JDK; all languages have map/dict. |
| `ConcurrentHashMap` | Thread-safe Map implementation. | JDK; Go: sync.Map; Node: lock or concurrent structure. |

---

## Configuration files

| Property prefix / key | Used by | Purpose |
|----------------------|--------|--------|
| `spring.application.name` | Spring Boot | App identity (logging, actuator, optional discovery). |
| `server.port` | Embedded server | Listen port. |
| `vnfm.service.url` | Our `VnfmServiceDestinations` | Logical URL for VNFM (Service Discovery). |
| `resilience4j.circuitbreaker.instances.*` | Resilience4j | Circuit breaker threshold, window, wait duration. |
| `management.endpoints.web.exposure.include` | Actuator | Which actuator endpoints are exposed (e.g. health, info). |

---

The code comments in each Java file go into more detail; this file is a quick lookup for "what is this annotation/type and what's the equivalent elsewhere?"
