package com.telecom.vnfm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * VNFM = VNF Manager. In a real 5G/telecom stack, the VNFM manages the lifecycle
 * of VNFs (start, stop, scale, health). Here we only expose a health API for the NFVO.
 *
 * SERVICE DISCOVERY ROLE: This service is the "target" of discovery. In Kubernetes,
 * the Service "vnfm-service" points to Pods running this app; NFVO calls that name.
 * In Docker Compose, the service name "vnfm" is used the same way.
 * ------------------------------------------------------------------------------------
 * ANNOTATIONS AND CLASSES: Same as NfvoApplication—@SpringBootApplication and
 * SpringApplication.run(...). See NfvoApplication.java for detailed explanation of
 * @SpringBootApplication (Configuration + EnableAutoConfiguration + ComponentScan)
 * and how it compares to Quarkus/Micronaut/plain Java.
 * ------------------------------------------------------------------------------------
 * This app uses spring-boot-starter-web (blocking servlet stack), not WebFlux, so
 * the embedded server is Tomcat by default. Controllers return plain objects (not
 * Mono/Flux); Spring MVC serialises them to JSON. For a simple provider service,
 * blocking is fine; the consumer (NFVO) is reactive to handle many concurrent
 * outbound calls without blocking threads.
 */
@SpringBootApplication
public class VnfmApplication {

    public static void main(String[] args) {
        SpringApplication.run(VnfmApplication.class, args);
    }
}
