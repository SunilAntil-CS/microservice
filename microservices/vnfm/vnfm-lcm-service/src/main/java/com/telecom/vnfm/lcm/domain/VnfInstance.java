package com.telecom.vnfm.lcm.domain;

import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;

import javax.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;

/**
 * AGGREGATE ROOT 1: VNF Instance (Lifecycle Management).
 * ---------------------------------------------------------------------------
 * STATE MACHINE (ETSI MANO): INSTANTIATING → DEPLOYING_INFRA → ACTIVE | FAILED.
 * - INSTANTIATING: Aggregate created; about to send InfraDeploymentRequestedEvent.
 * - DEPLOYING_INFRA: Request sent; waiting for VIM's 3-event lifecycle (ACK, Progress, Reply/Failed).
 * - ACTIVE: Infra deployed successfully; VNF is running.
 * - FAILED: Terminal failure (VIM reported failure or Timeout Watchdog expired).
 *
 * DDD ROLE: This is the root of the LCM aggregate. All lifecycle state and
 * business rules for a single VNF instance are encapsulated here. The aggregate
 * boundary is the unit of consistency: we never span a transaction across
 * VnfInstance and another aggregate (e.g. CloudDeployment); we coordinate
 * via events (InfraDeploymentRequestedEvent) and reference by identity only.
 *
 * STRICT BOUNDARIES (Richardson Ch. 5):
 * - We reference the VIM-side deployment only by deploymentId (String). We
 *   never hold a reference to the CloudDeployment entity or its database.
 * - The two aggregates live in separate databases (LCM DB vs VIM DB). Cross-
 *   aggregate updates happen asynchronously via Kafka and the Transactional Outbox.
 *
 * RICH DOMAIN MODEL:
 * - Logic lives inside the entity. requestDeployment(), markInfraDeployed(),
 *   and markInfraFailed() encapsulate state transitions. The service layer
 *   only loads, calls methods, saves, and publishes events—it does not manipulate
 *   fields via setters.
 *
 * OPTIMISTIC LOCKING (@Version):
 * - The version field is used to prevent the "Sam and Mary" concurrent update
 *   problem. If two operators (or two API calls) update the same VnfInstance
 *   concurrently, the second commit will fail with OptimisticLockException.
 */
@Entity
@Table(name = "vnf_instances")
public class VnfInstance {

    @Id
    @Column(name = "vnf_id", length = 64, nullable = false, updatable = false)
    private String vnfId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "vcpu", column = @Column(name = "profile_vcpu")),
            @AttributeOverride(name = "memoryMb", column = @Column(name = "profile_memory_mb")),
            @AttributeOverride(name = "softwareVersion", column = @Column(name = "profile_software_version"))
    })
    private VnfProfile profile;

    @Column(name = "deployment_id", length = 64)
    private String deploymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VnfState state;

    /**
     * Failure reason when state is FAILED (compensation or watchdog).
     * Used for VnfStatusUpdatedEvent message to NFVO.
     */
    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    /**
     * Last state-change time. Used by the Timeout Watchdog to find instances
     * stuck in DEPLOYING_INFRA for more than 15 minutes (Choreography mitigation).
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA requires a no-arg constructor. Protected so that creation is only
     * through the static factory requestDeployment(...).
     */
    protected VnfInstance() {
    }

    /**
     * Static factory: creates a new VnfInstance in DEPLOYING_INFRA state and
     * produces the domain event that will trigger the VIM Adapter to create
     * infrastructure. The event is not published here—the application service
     * publishes it atomically with the persist using the Transactional Outbox.
     *
     * WHY STATIC FACTORY INSTEAD OF CONSTRUCTOR?
     * - Encapsulates the creation invariant (state = DEPLOYING_INFRA) and
     *   the event generation in one place. Callers cannot create an instance
     *   in an invalid state. Also allows returning a ResultWithDomainEvents
     *   (constructors can only return the new object).
     */
    public static ResultWithDomainEvents<VnfInstance, InfraDeploymentRequestedEvent> requestDeployment(
            String vnfId, VnfProfile profile) {
        Objects.requireNonNull(vnfId, "vnfId must not be null");
        Objects.requireNonNull(profile, "profile must not be null");

        VnfInstance vnf = new VnfInstance();
        vnf.vnfId = vnfId;
        vnf.profile = profile;
        vnf.state = VnfState.DEPLOYING_INFRA;
        vnf.deploymentId = null;
        vnf.updatedAt = Instant.now();

        InfraDeploymentRequestedEvent event = new InfraDeploymentRequestedEvent(
                vnfId,
                profile.getVcpu(),
                profile.getMemoryMb(),
                profile.getSoftwareVersion()
        );

        return new ResultWithDomainEvents<>(vnf, Collections.singletonList(event));
    }

    /**
     * Called when the VIM Adapter has successfully created the CloudDeployment
     * and we receive a reply (e.g. InfraDeployedSuccessfullyEvent). Updates
     * the aggregate to ACTIVE and stores the deployment id for reference.
     */
    public void markInfraDeployed(String deploymentId) {
        if (this.state != VnfState.DEPLOYING_INFRA) {
            throw new IllegalStateException("Cannot mark deployed when state is " + this.state);
        }
        this.deploymentId = deploymentId;
        this.state = VnfState.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * COMPENSATION: Marks the VNF as FAILED (e.g. on InfraDeploymentFailedEvent
     * or when the Timeout Watchdog detects a stuck instance). Rollback path of
     * the Distributed Saga—no infrastructure was committed on LCM side; we only
     * update our aggregate and notify the NFVO via VnfStatusUpdatedEvent.
     */
    public void markInfraFailed(String reason) {
        if (this.state != VnfState.DEPLOYING_INFRA) {
            throw new IllegalStateException("Cannot mark failed when state is " + this.state);
        }
        this.state = VnfState.FAILED;
        this.failureReason = reason != null ? reason : "Unknown";
        this.updatedAt = Instant.now();
    }

    // --- Getters only (no public setters for domain fields) ---

    public String getVnfId() {
        return vnfId;
    }

    public Long getVersion() {
        return version;
    }

    public VnfProfile getProfile() {
        return profile;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public VnfState getState() {
        return state;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum VnfState {
        /** Initial state when creation is requested (before event is sent). */
        INSTANTIATING,
        /** Infrastructure creation requested; waiting for VIM to complete. */
        DEPLOYING_INFRA,
        /** VIM has created the deployment; VNF is active. */
        ACTIVE,
        /** Terminal failure (VIM failure or timeout). */
        FAILED
    }
}
