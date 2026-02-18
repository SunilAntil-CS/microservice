package com.vnfm.vim.simulator.api;

import jakarta.validation.constraints.Min;

/**
 * Request body for POST /servers (allocate VM).
 */
public class CreateServerRequest {

    @Min(0)
    private int cpu = 1;

    @Min(0)
    private int memory = 1024;

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
