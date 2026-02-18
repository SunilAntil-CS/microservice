package com.vnfm.vim.simulator.api;

/**
 * Response for allocated server (POST /servers) or GET /servers/{id}.
 */
public class ServerResponse {

    private String resourceId;
    private String ip;
    private String status;
    private int cpu;
    private int memory;

    public ServerResponse() {
    }

    public ServerResponse(String resourceId, String ip, String status, int cpu, int memory) {
        this.resourceId = resourceId;
        this.ip = ip;
        this.status = status;
        this.cpu = cpu;
        this.memory = memory;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCpu() {
        return cpu;
    }

    public void setCpu(int cpu) {
        this.cpu = cpu;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }
}
