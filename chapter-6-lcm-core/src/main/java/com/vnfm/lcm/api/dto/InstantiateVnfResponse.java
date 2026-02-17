package com.vnfm.lcm.api.dto;

/**
 * Response for POST /api/vnfs (202 Accepted).
 * Includes vnfId and statusUrl; Location header points to /api/vnfs/{vnfId}/status.
 */
public class InstantiateVnfResponse {

    private String vnfId;
    private String statusUrl;
    private String message;

    public InstantiateVnfResponse() {
    }

    public InstantiateVnfResponse(String vnfId, String statusUrl, String message) {
        this.vnfId = vnfId;
        this.statusUrl = statusUrl;
        this.message = message;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
