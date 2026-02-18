package com.cqrs.policyquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableConfigurationProperties(PolicyQueryProperties.class)
@EnableRetry
public class PolicyQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyQueryApplication.class, args);
    }
}
