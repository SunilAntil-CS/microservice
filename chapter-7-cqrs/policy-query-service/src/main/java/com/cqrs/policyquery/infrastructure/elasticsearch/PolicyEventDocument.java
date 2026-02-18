package com.cqrs.policyquery.infrastructure.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

/**
 * Elasticsearch document for policy history. Index name is dynamic (per day) at write time.
 */
@Document(indexName = "policy-history")
public class PolicyEventDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String subscriberId;

    @Field(type = FieldType.Date)
    private Instant timestamp;

    @Field(type = FieldType.Keyword)
    private String policyName;

    @Field(type = FieldType.Boolean)
    private boolean decision;

    @Field(type = FieldType.Long)
    private long quotaUsed;

    public PolicyEventDocument() {
    }

    public PolicyEventDocument(String id, String subscriberId, Instant timestamp, String policyName, boolean decision, long quotaUsed) {
        this.id = id;
        this.subscriberId = subscriberId;
        this.timestamp = timestamp;
        this.policyName = policyName;
        this.decision = decision;
        this.quotaUsed = quotaUsed;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSubscriberId() { return subscriberId; }
    public void setSubscriberId(String subscriberId) { this.subscriberId = subscriberId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public boolean isDecision() { return decision; }
    public void setDecision(boolean decision) { this.decision = decision; }
    public long getQuotaUsed() { return quotaUsed; }
    public void setQuotaUsed(long quotaUsed) { this.quotaUsed = quotaUsed; }
}
