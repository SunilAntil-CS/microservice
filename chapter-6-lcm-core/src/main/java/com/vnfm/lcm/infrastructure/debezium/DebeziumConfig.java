package com.vnfm.lcm.infrastructure.debezium;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DebeziumProperties.class)
public class DebeziumConfig {
}
