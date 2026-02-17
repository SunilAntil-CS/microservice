package com.telecom.vnfm.vim.domain;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cloud_deployments")
public class CloudDeployment {

    @Id
    @Column(name = "deployment_id", length = 64, nullable = false, updatable = false)
    private String deploymentId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "vnf_id", length = 64, nullable = false)
    private String vnfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentState state;

    protected CloudDeployment() {
    }

    public static CloudDeployment startDeployment(String vnfId) {
        Objects.requireNonNull(vnfId);
        CloudDeployment d = new CloudDeployment();
        d.deploymentId = UUID.randomUUID().toString();
        d.vnfId = vnfId;
        d.state = DeploymentState.CREATING;
        return d;
    }

    public String getDeploymentId() { return deploymentId; }
    public String getVnfId() { return vnfId; }
    public DeploymentState getState() { return state; }

    public enum DeploymentState {
        CREATING, RUNNING
    }
}
