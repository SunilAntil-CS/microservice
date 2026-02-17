package com.vnfm.lcm.api.dto;

/**
 * Request body for POST /vnflcm/v1/vnf_instances (ETSI SOL002/003).
 * Optional fields for VNF instance creation.
 */
public class CreateVnfInstanceRequest {

    private String vnfInstanceName;
    private String vnfInstanceDescription;

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
}
