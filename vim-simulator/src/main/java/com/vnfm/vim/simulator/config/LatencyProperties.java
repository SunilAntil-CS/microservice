package com.vnfm.vim.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "latency")
public class LatencyProperties {

    /** Min simulated delay in milliseconds. */
    private int minMs = 0;

    /** Max simulated delay in milliseconds. */
    private int maxMs = 100;

    public int getMinMs() {
        return minMs;
    }

    public void setMinMs(int minMs) {
        this.minMs = minMs;
    }

    public int getMaxMs() {
        return maxMs;
    }

    public void setMaxMs(int maxMs) {
        this.maxMs = maxMs;
    }
}
