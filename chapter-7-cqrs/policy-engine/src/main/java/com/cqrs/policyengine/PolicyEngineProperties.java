package com.cqrs.policyengine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy-engine")
public class PolicyEngineProperties {

    /** Interval in ms between emitting random policy events. */
    private long emitIntervalMs = 5000;

    public long getEmitIntervalMs() {
        return emitIntervalMs;
    }

    public void setEmitIntervalMs(long emitIntervalMs) {
        this.emitIntervalMs = emitIntervalMs;
    }
}
