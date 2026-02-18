package com.cqrs.policyquery;

import com.cqrs.policyquery.infrastructure.elasticsearch.PolicyEventDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for policy history REST API (Testcontainers ES + EmbeddedKafka).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = { "policy-events", "policy-events-dlq" }, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PolicyHistoryControllerTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    )
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Test
    void getHistory_empty_returnsPage() throws Exception {
        mockMvc.perform(get("/api/policy-history").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalCount").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getHistory_afterIndexing_returnsDocument() throws Exception {
        String indexName = "policy-history-2025-01-15";
        PolicyEventDocument doc = new PolicyEventDocument(
                "evt-1", "sub-001", Instant.parse("2025-01-15T12:00:00Z"),
                "QuotaPolicy", true, 100L
        );
        IndexQuery query = new IndexQueryBuilder().withId(doc.getId()).withObject(doc).build();
        elasticsearchOperations.index(query, IndexCoordinates.of(indexName));

        mockMvc.perform(get("/api/policy-history/sub-001").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.content[0].subscriberId").value("sub-001"))
                .andExpect(jsonPath("$.content[0].policyName").value("QuotaPolicy"));
    }
}
