package com.cqrs.policyquery.infrastructure.elasticsearch;

import com.cqrs.policyquery.config.PolicyQueryProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Provides the daily index name for policy history (e.g. policy-history-2025-02-18).
 */
@Component
public class PolicyIndexNameProvider {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final PolicyQueryProperties properties;

    public PolicyIndexNameProvider(PolicyQueryProperties properties) {
        this.properties = properties;
    }

    public String indexName() {
        return indexName(Instant.now());
    }

    public String indexName(Instant instant) {
        return properties.getElasticsearch().getIndexPrefix() + "-" + FORMAT.format(instant);
    }
}
