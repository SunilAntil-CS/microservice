package com.vnfm.lcm.api.dto;

/**
 * Response for VNF instantiation (northbound API).
 * Returned by the controller and cached for idempotency (duplicate requestId returns this).
 */
public class InstantiateResponse {

    private String requestId;
    private String vnfId;
    private String status;
    private String message;

    public InstantiateResponse() {
    }

    public InstantiateResponse(String requestId, String vnfId, String status, String message) {
        this.requestId = requestId;
        this.vnfId = vnfId;
        this.status = status;
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
