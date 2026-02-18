package com.cqrs.policyquery.application;

import com.cqrs.policyquery.config.PolicyQueryProperties;
import com.cqrs.policyquery.infrastructure.elasticsearch.PolicyEventDocument;
import com.cqrs.policyquery.interfaces.PolicyHistoryPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Queries indexed policy history from Elasticsearch with filters and pagination.
 */
@Service
public class PolicyHistoryQueryService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final PolicyQueryProperties properties;

    private static final String INDEX_PATTERN = "policy-history-*";

    public PolicyHistoryQueryService(ElasticsearchOperations elasticsearchOperations,
                                     PolicyQueryProperties properties) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.properties = properties;
    }

    public PolicyHistoryPage query(String subscriberId, Instant from, Instant to, String policyName, int page, int size) {
        size = Math.min(size, properties.getPagination().getMaxSize());
        Criteria criteria = new Criteria();

        if (subscriberId != null && !subscriberId.isBlank()) {
            criteria = criteria.and(Criteria.where("subscriberId").is(subscriberId));
        }
        if (from != null) {
            criteria = criteria.and(Criteria.where("timestamp").greaterThanEqual(from));
        }
        if (to != null) {
            criteria = criteria.and(Criteria.where("timestamp").lessThanEqual(to));
        }
        if (policyName != null && !policyName.isBlank()) {
            criteria = criteria.and(Criteria.where("policyName").is(policyName));
        }

        Query query = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(page, size));

        SearchHits<PolicyEventDocument> hits = elasticsearchOperations.search(query, PolicyEventDocument.class, IndexCoordinates.of(INDEX_PATTERN));

        List<PolicyEventDocument> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long total = hits.getTotalHits();
        return new PolicyHistoryPage(
                content,
                total,
                page,
                size,
                (long) (page + 1) * size < total
        );
    }
}
