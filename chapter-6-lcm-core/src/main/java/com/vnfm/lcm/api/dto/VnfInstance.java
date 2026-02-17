package com.vnfm.lcm.api.dto;

import java.time.Instant;

/**
 * ETSI SOL002/003 VNF Instance representation for GET /vnflcm/v1/vnf_instances/{vnfId}.
 */
public class VnfInstance {

    private String id;
    private String instantiationState;  // NOT_INSTANTIATED, INSTANTIATED
    private String vnfInstanceName;
    private String vnfInstanceDescription;
    private String vimResourceId;
    private String ipAddress;
    private Instant createdAt;

    public VnfInstance() {
    }

    public VnfInstance(String id, String instantiationState, String vnfInstanceName,
                       String vnfInstanceDescription, String vimResourceId, String ipAddress,
                       Instant createdAt) {
        this.id = id;
        this.instantiationState = instantiationState;
        this.vnfInstanceName = vnfInstanceName;
        this.vnfInstanceDescription = vnfInstanceDescription;
        this.vimResourceId = vimResourceId;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInstantiationState() {
        return instantiationState;
    }

    public void setInstantiationState(String instantiationState) {
        this.instantiationState = instantiationState;
    }

    public String getVnfInstanceName() {
        return vnfInstanceName;
    }

    public void setVnfInstanceName(String vnfInstanceName) {
        this.vnfInstanceName = vnfInstanceName;
    }

    public String getVnfInstanceDescription() {
        return vnfInstanceDescription;
    }

    public void setVnfInstanceDescription(String vnfInstanceDescription) {
        this.vnfInstanceDescription = vnfInstanceDescription;
    }

    public String getVimResourceId() {
        return vimResourceId;
    }

    public void setVimResourceId(String vimResourceId) {
        this.vimResourceId = vimResourceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
