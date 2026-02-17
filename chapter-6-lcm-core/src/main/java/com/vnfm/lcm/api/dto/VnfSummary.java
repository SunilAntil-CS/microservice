package com.vnfm.lcm.api.dto;

/**
 * Summary of a VNF for GET /api/vnfs (list all).
 */
public class VnfSummary {

    private String vnfId;
    private String state;

    public VnfSummary() {
    }

    public VnfSummary(String vnfId, String state) {
        this.vnfId = vnfId;
        this.state = state;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
