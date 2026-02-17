package com.vnfm.lcm.infrastructure.idempotency;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * STUDY NOTE – Idempotency cache (processed_requests table)
 * --------------------------------------------------------
 * Stores the response body (JSON) for each successfully processed requestId.
 * When a duplicate request arrives (same requestId), the filter returns this
 * cached response without re-executing the operation.
 *
 * JPA: @Table name and column names match DB; @Lob for response_cache (can be large).
 */
@Entity
@Table(name = "processed_requests", indexes = {
        @Index(name = "idx_processed_requests_request_id", columnList = "request_id", unique = true)
})
public class ProcessedRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    /** Cached response body (JSON) returned for this request. */
    @Lob
    @Column(name = "response_cache", nullable = false)
    private String responseCache;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @SuppressWarnings("unused")
    public ProcessedRequest() {
    }

    public ProcessedRequest(String requestId, String responseCache, Instant processedAt) {
        this.requestId = requestId;
        this.responseCache = responseCache;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResponseCache() {
        return responseCache;
    }

    public void setResponseCache(String responseCache) {
        this.responseCache = responseCache;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
