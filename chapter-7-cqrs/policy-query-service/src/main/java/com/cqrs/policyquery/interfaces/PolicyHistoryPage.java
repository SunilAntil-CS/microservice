package com.cqrs.policyquery.interfaces;

import com.cqrs.policyquery.infrastructure.elasticsearch.PolicyEventDocument;

import java.util.List;

/**
 * Page result for policy history API.
 */
public record PolicyHistoryPage(
        List<PolicyEventDocument> content,
        long totalCount,
        int page,
        int size,
        boolean hasNext
) {}
