package com.telecom.vnfm.lcm.domain;

import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;

import javax.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;

/**
 * AGGREGATE ROOT: VNF Instance. State: INSTANTIATING → DEPLOYING_INFRA → ACTIVE | FAILED.
 * Timeout Watchdog uses updatedAt to mark stuck instances FAILED (Choreography mitigation).
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

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VnfInstance() {
    }

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
                vnfId, profile.getVcpu(), profile.getMemoryMb(), profile.getSoftwareVersion());
        return new ResultWithDomainEvents<>(vnf, Collections.singletonList(event));
    }

    public void markInfraDeployed(String deploymentId) {
        if (this.state != VnfState.DEPLOYING_INFRA) {
            throw new IllegalStateException("Cannot mark deployed when state is " + this.state);
        }
        this.deploymentId = deploymentId;
        this.state = VnfState.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void markInfraFailed(String reason) {
        if (this.state != VnfState.DEPLOYING_INFRA) {
            throw new IllegalStateException("Cannot mark failed when state is " + this.state);
        }
        this.state = VnfState.FAILED;
        this.failureReason = reason != null ? reason : "Unknown";
        this.updatedAt = Instant.now();
    }

    public String getVnfId() { return vnfId; }
    public Long getVersion() { return version; }
    public VnfProfile getProfile() { return profile; }
    public String getDeploymentId() { return deploymentId; }
    public VnfState getState() { return state; }
    public String getFailureReason() { return failureReason; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum VnfState {
        INSTANTIATING, DEPLOYING_INFRA, ACTIVE, FAILED
    }
}
