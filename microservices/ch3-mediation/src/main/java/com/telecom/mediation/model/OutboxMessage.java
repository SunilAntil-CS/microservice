package com.telecom.mediation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * MODULE 3: The Outbox table (same schema as in the Notes).
 *
 * CONCEPT: We do NOT send to Kafka inside the business transaction. We INSERT
 * into this table in the same @Transactional method that saves the CDR. So either
 * both the CDR and this row are committed, or neither. A separate relay process
 * (OutboxRelay) later SELECTs WHERE published = 0, sends payload to Kafka, and
 * sets published = 1 (or deletes the row).
 *
 * Schema matches the Notes: id, destination (topic), headers, payload (JSON),
 * published (0/1), creation_time. LONGTEXT in SQL → @Lob or long varchar; we use
 * columnDefinition for H2 compatibility.
 *
 * @Entity @Table(name = "message"): Table name "message" as in the Notes so the
 * concept (outbox = message table) is clear. JPA will create/validate this table
 * if we use spring.jpa.hibernate.ddl-auto=update or run schema.sql.
 */
@Entity
@Table(name = "message")
public class OutboxMessage {

    @Id
    private String id;

    /** Topic / destination name (e.g. "billing-events" or "com.telecom.mediation.Cdr"). */
    @Column(nullable = false, length = 1000)
    private String destination;

    @Column(length = 1000)
    private String headers;

    /** JSON payload — the serialised CdrProcessedEvent (or any domain event). */
    @Column(nullable = false, length = 65535)
    private String payload;

    /** 0 = not yet sent, 1 = sent to Kafka. Relay updates to 1 after successful send. */
    @Column(nullable = false)
    private int published = 0;

    private Long creationTime;

    public OutboxMessage() {}

    public OutboxMessage(String id, String destination, String headers, String payload) {
        this.id = id;
        this.destination = destination;
        this.headers = headers != null ? headers : "{}";
        this.payload = payload;
        this.published = 0;
        this.creationTime = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public int getPublished() { return published; }
    public void setPublished(int published) { this.published = published; }
    public Long getCreationTime() { return creationTime; }
    public void setCreationTime(Long creationTime) { this.creationTime = creationTime; }
}
