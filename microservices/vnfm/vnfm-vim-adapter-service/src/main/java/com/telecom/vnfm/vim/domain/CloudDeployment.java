package com.telecom.vnfm.vim.domain;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * AGGREGATE ROOT 2: Cloud Deployment (VIM Adapter).
 * ---------------------------------------------------------------------------
 * DDD ROLE: This is the root of the VIM aggregate. It represents the
 * infrastructure side of a VNF: the actual deployment (e.g. Kubernetes pods on
 * Cisco UCS). It lives in a separate service and database from VnfInstance.
 * We reference the LCM aggregate only by vnfId (String)—never a foreign key
 * or reference to the other database.
 *
 * STRICT BOUNDARIES:
 * - CloudDeployment is in the VIM Adapter's database. The LCM service never
 *   touches this table. Coordination is via events (InfraDeploymentRequestedEvent
 *   from Kafka) and reply events back to LCM.
 *
 * DOMAIN PURITY:
 * - This aggregate contains only business concepts: deploymentId, vnfId, status.
 *   Idempotency (message_id tracking) is handled by the dedicated Idempotency
 *   Shield (ProcessedMessageEntity) in com.telecom.vnfm.vim.idempotency—no
 *   messaging concerns leak into the domain.
 *
 * OPTIMISTIC LOCKING (@Version):
 * - Protects against concurrent updates to the same deployment record
 *   (e.g. two threads trying to mark running).
 *
 * NO PUBLIC SETTERS:
 * - State changes go through markRunning(). The factory startDeployment(...)
 *   is the only way to create an instance.
 */
@Entity
@Table(name = "cloud_deployments")
public class CloudDeployment {

    @Id
    @Column(name = "deployment_id", length = 64, nullable = false, updatable = false)
    private String deploymentId;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Reference to the VnfInstance in the LCM service (by id only). We never
     * hold a reference to the VnfInstance entity or its database.
     */
    @Column(name = "vnf_id", length = 64, nullable = false)
    private String vnfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentState state;

    protected CloudDeployment() {
    }

    /**
     * Static factory: creates a new CloudDeployment in CREATING state.
     * Called by the idempotent handler after the Idempotency Shield has
     * accepted the message (see VimEventHandler).
     *
     * @param vnfId Identity of the VNF in the LCM service.
     */
    public static CloudDeployment startDeployment(String vnfId) {
        Objects.requireNonNull(vnfId, "vnfId must not be null");

        CloudDeployment d = new CloudDeployment();
        d.deploymentId = UUID.randomUUID().toString();
        d.vnfId = vnfId;
        d.state = DeploymentState.CREATING;
        return d;
    }

    /**
     * Marks the deployment as RUNNING after the infrastructure (e.g. pods)
     * has been successfully created. Called by the handler after the external
     * VIM/Kubernetes API call succeeds.
     */
    public void markRunning() {
        if (this.state != DeploymentState.CREATING) {
            throw new IllegalStateException("Cannot mark running when state is " + this.state);
        }
        this.state = DeploymentState.RUNNING;
    }

    public String getDeploymentId() { return deploymentId; }
    public Long getVersion() { return version; }
    public String getVnfId() { return vnfId; }
    public DeploymentState getState() { return state; }

    public enum DeploymentState {
        CREATING,
        RUNNING
    }
}
