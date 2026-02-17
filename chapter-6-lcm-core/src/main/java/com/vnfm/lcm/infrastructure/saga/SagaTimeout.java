package com.vnfm.lcm.infrastructure.saga;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persistent timeout for a saga step. When executeAt is reached without a reply,
 * the TimeoutScheduler triggers compensation (e.g. handleReply with failure).
 * Marked processed when reply arrives or when timeout has been handled.
 */
@Entity
@Table(name = "saga_timeouts", indexes = {
        @Index(name = "idx_saga_timeouts_execute_processed", columnList = "processed, execute_at")
})
public class SagaTimeout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Column(name = "step", nullable = false)
    private int step;

    @Column(name = "execute_at", nullable = false)
    private Instant executeAt;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("unused")
    public SagaTimeout() {
    }

    public SagaTimeout(String sagaId, int step, Instant executeAt) {
        this.sagaId = sagaId;
        this.step = step;
        this.executeAt = executeAt;
        this.processed = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public Instant getExecuteAt() {
        return executeAt;
    }

    public void setExecuteAt(Instant executeAt) {
        this.executeAt = executeAt;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
