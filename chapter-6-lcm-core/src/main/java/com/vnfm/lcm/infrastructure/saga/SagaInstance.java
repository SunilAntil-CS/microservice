package com.vnfm.lcm.infrastructure.saga;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga instance persisted in saga_instances table.
 * sagaState is stored as JSON (e.g. completed steps, step results) for flexibility.
 */
@Entity
@Table(name = "saga_instances", indexes = {
        @Index(name = "idx_saga_instances_saga_id", columnList = "saga_id", unique = true),
        @Index(name = "idx_saga_instances_vnf_id_status", columnList = "vnf_id, status")
})
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false, unique = true, length = 36)
    private String sagaId;

    @Column(name = "vnf_id", nullable = false, length = 64)
    private String vnfId;

    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "saga_type", nullable = false, length = 64)
    private String sagaType;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    /** JSON state: completed steps, step results, etc. */
    @Lob
    @Column(name = "saga_state")
    private String sagaState;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SagaStatus status = SagaStatus.RUNNING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @SuppressWarnings("unused")
    public SagaInstance() {
    }

    public SagaInstance(UUID sagaId, String vnfId, String sagaType, int currentStep, String sagaState) {
        this(sagaId, vnfId, null, sagaType, currentStep, sagaState);
    }

    public SagaInstance(UUID sagaId, String vnfId, UUID operationId, String sagaType, int currentStep, String sagaState) {
        this.sagaId = sagaId.toString();
        this.vnfId = vnfId;
        this.operationId = operationId != null ? operationId.toString() : null;
        this.sagaType = sagaType;
        this.currentStep = currentStep;
        this.sagaState = sagaState != null ? sagaState : "{}";
        this.status = SagaStatus.RUNNING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSagaType() {
        return sagaType;
    }

    public void setSagaType(String sagaType) {
        this.sagaType = sagaType;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public String getSagaState() {
        return sagaState;
    }

    public void setSagaState(String sagaState) {
        this.sagaState = sagaState;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
