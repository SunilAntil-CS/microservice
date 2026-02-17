package com.vnfm.lcm.infrastructure.eventstore;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * REVISION – JPA Entity (snapshots table)
 * ---------------------------------------
 * Same concepts as EventEntity: @Entity (table mapping), @Id, @GeneratedValue(IDENTITY),
 * @Column, @Lob. This table holds optional snapshots of aggregate state at a given version
 * so we can rebuild quickly by loading snapshot + only events after that version.
 */
@Entity
@Table(name = "snapshots", indexes = {
        @Index(name = "idx_snapshots_aggregate_version", columnList = "aggregate_id, version")
})
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "version", nullable = false)
    private int version;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** REVISION: No-arg constructor required by JPA for entity instantiation. */
    public SnapshotEntity() {
    }

    public SnapshotEntity(String aggregateId, int version, String payload, Instant createdAt) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
