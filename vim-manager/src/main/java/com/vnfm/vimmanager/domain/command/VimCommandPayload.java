package com.vnfm.vimmanager.domain.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Generic payload for VIM commands (ReserveResources, ReleaseResources).
 * Matches what LCM sends: sagaId, vnfId, resources, reason (for release), etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VimCommandPayload {

    private String sagaId;
    private String vnfId;
    private String operationId;
    private Map<String, Object> resources;
    private String reason;

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public Map<String, Object> getResources() {
        return resources;
    }

    public void setResources(Map<String, Object> resources) {
        this.resources = resources;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
