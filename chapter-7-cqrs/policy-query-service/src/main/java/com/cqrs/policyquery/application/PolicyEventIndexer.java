package com.cqrs.policyquery.application;

import com.cqrs.policyquery.domain.PolicyEventPayload;
import com.cqrs.policyquery.infrastructure.elasticsearch.PolicyEventDocument;
import com.cqrs.policyquery.infrastructure.elasticsearch.PolicyIndexNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexedObjectInformation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Indexes policy events in Elasticsearch with idempotency (opType CREATE).
 * Uses daily index: policy-history-yyyy-MM-dd. Retries on transient failures.
 */
@Service
public class PolicyEventIndexer {

    private static final Logger log = LoggerFactory.getLogger(PolicyEventIndexer.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final PolicyIndexNameProvider indexNameProvider;

    public PolicyEventIndexer(ElasticsearchOperations elasticsearchOperations,
                              PolicyIndexNameProvider indexNameProvider) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexNameProvider = indexNameProvider;
    }

    @Retryable(
            retryFor = { org.springframework.data.elasticsearch.ResourceNotFoundException.class, Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void index(PolicyEventPayload event) {
        String indexName = indexNameProvider.indexName(event.timestamp());
        String docId = event.eventId().toString();

        PolicyEventDocument doc = new PolicyEventDocument(
                docId,
                event.subscriberId(),
                event.timestamp(),
                event.policyName(),
                event.decision(),
                event.quotaUsed()
        );

        IndexQuery query = new IndexQueryBuilder()
                .withId(docId)
                .withObject(doc)
                .build();

        // Idempotency: if document already exists (same eventId), skip
        if (elasticsearchOperations.get(docId, PolicyEventDocument.class, IndexCoordinates.of(indexName)) != null) {
            log.debug("Duplicate event (idempotent skip) eventId={}", docId);
            return;
        }

        IndexedObjectInformation result = elasticsearchOperations.index(query, IndexCoordinates.of(indexName));
        log.debug("Indexed policy event eventId={} index={} resultId={}", docId, indexName, result.id());
    }
}
