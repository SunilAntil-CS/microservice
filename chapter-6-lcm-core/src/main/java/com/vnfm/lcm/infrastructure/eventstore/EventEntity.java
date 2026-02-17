package com.vnfm.lcm.infrastructure.eventstore;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * REVISION – JPA Entity (events table)
 * -------------------------------------
 * @Entity: Marks this class as a JPA entity. JPA (Hibernate) will map it to a DB table.
 *          The entity class is "metadata for persistence": table name, column names, PK, etc.
 *          We do NOT inject EventEntity as a bean; we create instances (new EventEntity(...))
 *          and pass them to the repository.
 *
 * @Table: Specifies the table name and indexes. Indexes speed up queries by aggregate_id and version.
 *
 * @Id: The primary key of the table. Every entity must have one.
 *
 * @GeneratedValue(strategy = IDENTITY): The DB generates the id (e.g. auto-increment). We don't set it.
 *
 * @Column: Maps the field to a column. name = DB column name; nullable/length are DDL hints.
 *
 * @Lob: Large object – for payload we use a large text/CLOB column (not a short varchar).
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_aggregate_version", columnList = "aggregate_id, aggregate_type, version")
})
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType = "VNF";

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    /** REVISION: JPA requires a no-arg constructor so it can instantiate entities (e.g. when loading from DB). */
    public EventEntity() {
    }

    public EventEntity(String eventId, String aggregateId, String aggregateType, int version, String eventType, String payload, Instant eventTimestamp) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType != null ? aggregateType : "VNF";
        this.version = version;
        this.eventType = eventType;
        this.payload = payload;
        this.eventTimestamp = eventTimestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
}
