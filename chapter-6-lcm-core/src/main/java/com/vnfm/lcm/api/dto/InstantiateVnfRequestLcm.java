package com.vnfm.lcm.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Request body for POST /vnflcm/v1/vnf_instances/{vnfId}/instantiate (ETSI SOL002/003).
 * Includes flavourId, instantiationLevelId, extVirtualLinks, and optional requestId for idempotency.
 */
public class InstantiateVnfRequestLcm {

    private String requestId;
    private String flavourId;
    private String instantiationLevelId;
    private List<Map<String, Object>> extVirtualLinks;
    private Map<String, Object> additionalParams;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getFlavourId() {
        return flavourId;
    }

    public void setFlavourId(String flavourId) {
        this.flavourId = flavourId;
    }

    public String getInstantiationLevelId() {
        return instantiationLevelId;
    }

    public void setInstantiationLevelId(String instantiationLevelId) {
        this.instantiationLevelId = instantiationLevelId;
    }

    public List<Map<String, Object>> getExtVirtualLinks() {
        return extVirtualLinks;
    }

    public void setExtVirtualLinks(List<Map<String, Object>> extVirtualLinks) {
        this.extVirtualLinks = extVirtualLinks;
    }

    public Map<String, Object> getAdditionalParams() {
        return additionalParams;
    }

    public void setAdditionalParams(Map<String, Object> additionalParams) {
        this.additionalParams = additionalParams;
    }
}
