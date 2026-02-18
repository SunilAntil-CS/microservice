package com.cqrs.policyengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PolicyEngineProperties.class)
public class PolicyEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyEngineApplication.class, args);
    }
}
