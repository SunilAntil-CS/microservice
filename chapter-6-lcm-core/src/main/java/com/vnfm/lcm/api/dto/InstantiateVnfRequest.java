package com.vnfm.lcm.api.dto;

/**
 * Request body for POST /api/vnfs (instantiate VNF).
 * requestId is used for idempotency (IdempotencyFilter).
 */
public class InstantiateVnfRequest {

    private String requestId;
    private String vnfType;
    private int cpuCores;
    private int memoryGb;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getVnfType() {
        return vnfType;
    }

    public void setVnfType(String vnfType) {
        this.vnfType = vnfType;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getMemoryGb() {
        return memoryGb;
    }

    public void setMemoryGb(int memoryGb) {
        this.memoryGb = memoryGb;
    }
}
