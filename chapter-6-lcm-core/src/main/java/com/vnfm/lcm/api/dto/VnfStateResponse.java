package com.vnfm.lcm.api.dto;

/**
 * Current VNF state (projected from event store) for GET /api/vnfs/{vnfId}.
 */
public class VnfStateResponse {

    private String vnfId;
    private String state;
    private int version;
    private String vimResourceId;
    private String ipAddress;

    public VnfStateResponse() {
    }

    public VnfStateResponse(String vnfId, String state, int version, String vimResourceId, String ipAddress) {
        this.vnfId = vnfId;
        this.state = state;
        this.version = version;
        this.vimResourceId = vimResourceId;
        this.ipAddress = ipAddress;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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
}
