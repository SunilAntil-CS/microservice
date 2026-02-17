package com.telecom.mediation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA Entity: persisted CDR for legal audit. Stored in the same DB as the outbox.
 *
 * CONCEPT: One transaction inserts both this row and the outbox row. If either
 * fails, both roll back — no "saved CDR but no billing event" (revenue leakage).
 *
 * @Entity: JPA (Jakarta Persistence). This class is mapped to a table; the name
 * defaults to "cdr_entity" (camelCase → snake_case). Use @Table(name="cdr") to override.
 * @Table(name = "cdr"): Table name in the DB. Keeps SQL and domain language clear.
 * @Id: Primary key. We generate UUID in application code so we can use the same id
 * in the outbox payload (idempotent consumer can key off it).
 * @Column: Optional; use for length, nullable, etc. We use it for decimal precision.
 *
 * In other frameworks: Quarkus uses same JPA annotations; Micronaut has @MappedEntity;
 * in Node/TypeORM you'd use decorators or schema definition.
 */
@Entity
@Table(name = "cdr")
public class CdrEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String callId;

    @Column(nullable = false)
    private String subscriberId;

    @Column(nullable = false)
    private Long durationSeconds;

    private String cellId;

    /**
     * Cost in currency units (e.g. cents). Billing service will charge this.
     * BigDecimal for money; avoid float/double. Precision 10, scale 2.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    public CdrEntity() {}

    /** Build entity from raw CDR; assign UUID and a simple cost (e.g. duration * rate). */
    public CdrEntity(RawCdr raw) {
        this.id = UUID.randomUUID().toString();
        this.callId = raw.callId();
        this.subscriberId = raw.subscriberId();
        this.durationSeconds = raw.durationSeconds();
        this.cellId = raw.cellId();
        this.cost = BigDecimal.valueOf(raw.durationSeconds() * 0.01); // example: 1 cent per second
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getSubscriberId() { return subscriberId; }
    public void setSubscriberId(String subscriberId) { this.subscriberId = subscriberId; }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getCellId() { return cellId; }
    public void setCellId(String cellId) { this.cellId = cellId; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
}
