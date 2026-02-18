package com.vnfm.vim.simulator.domain;

/**
 * In-memory representation of an allocated VM.
 */
public class Server {

    private final String resourceId;
    private final String ip;
    private final int cpu;
    private final int memory;
    private final String status;

    public Server(String resourceId, String ip, int cpu, int memory, String status) {
        this.resourceId = resourceId;
        this.ip = ip;
        this.cpu = cpu;
        this.memory = memory;
        this.status = status;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getIp() {
        return ip;
    }

    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public String getStatus() {
        return status;
    }
}
