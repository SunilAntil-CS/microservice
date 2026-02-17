package com.telecom.nfvo.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MODULE 2: Service Discovery Configuration (Type-Safe).
 *
 * WHY THIS CLASS? Single source of truth for "where is VNFM?"—no @Value in every class.
 * HOW: application.properties has vnfm.service.url=...; Spring binds it to the "url" field.
 * FAIL FAST: @NotBlank ensures app won't start if URL is missing (see below).
 * ------------------------------------------------------------------------------------
 * SPRING ANNOTATIONS:
 * ------------------------------------------------------------------------------------
 * @Configuration
 *   - Marks this class as a source of bean definitions. Spring creates a single instance
 *     (bean) and uses it for dependency injection. Also allows @Bean methods if needed.
 *   - In other frameworks: Quarkus @ApplicationScoped; Micronaut @Singleton; in Guice
 *     you'd bind the class in a Module.
 *
 * @ConfigurationProperties(prefix = "vnfm.service")
 *   - Binds all properties whose names start with "vnfm.service" to this class.
 *   - Property "vnfm.service.url" → field "url" (setter setUrl(...) is called).
 *   - Relaxed binding: vnfm.service.url in .properties can also be set via env
 *     VNFM_SERVICE_URL (uppercase, underscores). Spring normalises and maps it.
 *   - In other frameworks: Quarkus @ConfigMapping or @ConfigProperty; Micronaut
 *     @ConfigurationProperties; in Node (NestJS) you'd use ConfigService with a schema.
 * ------------------------------------------------------------------------------------
 * JAKARTA VALIDATION (Bean Validation / JSR 380):
 * ------------------------------------------------------------------------------------
 * @NotBlank(message = "...")
 *   - From jakarta.validation.constraints (Java EE / Jakarta EE). Validates that the
 *     value is not null, and not empty after trimming. If validation fails at startup,
 *     Spring throws and the application does not start (Fail Fast).
 *   - Requires dependency: spring-boot-starter-validation (which brings Hibernate Validator).
 *   - In other frameworks: Same annotations work in Quarkus/Micronaut; in Node you might
 *     use class-validator or Joi; in Go you'd validate in init or config load.
 * ------------------------------------------------------------------------------------
 * TYPE: String url
 *   - Java built-in type. Holds the logical URL of the VNFM (e.g. "http://vnfm-service").
 * ------------------------------------------------------------------------------------
 * Getter/Setter: Spring's @ConfigurationProperties binding uses the setter to inject
 * the value. The getter is used by our code (e.g. VnfmServiceProxy) to read the URL.
 */
@Configuration
@ConfigurationProperties(prefix = "vnfm.service")
public class VnfmServiceDestinations {

    @NotBlank(message = "vnfm.service.url must be set for service discovery")
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
