package com.vnfm.vim.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pool")
public class PoolProperties {

    /** Max number of VMs in the pool. */
    private int maxServers = 100;

    public int getMaxServers() {
        return maxServers;
    }

    public void setMaxServers(int maxServers) {
        this.maxServers = maxServers;
    }
}
