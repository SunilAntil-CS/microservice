package com.vnfm.vimmanager.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vim.simulator")
public class VimSimulatorProperties {

    /**
     * When true, simulator returns success; when false, returns failure.
     */
    private boolean succeed = true;

    /**
     * When true, the in-memory simulator is used as VimClient. Can be disabled to use a real adapter.
     */
    private boolean enabled = true;

    public boolean isSucceed() {
        return succeed;
    }

    public void setSucceed(boolean succeed) {
        this.succeed = succeed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
