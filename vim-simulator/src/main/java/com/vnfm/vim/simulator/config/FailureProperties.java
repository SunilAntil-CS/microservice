package com.vnfm.vim.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "failure")
public class FailureProperties {

    /** Probability of failure (0.0–1.0). */
    private double rate = 0.0;

    /** Error types when failure triggers (e.g. TIMEOUT, QUOTA, INTERNAL). */
    private List<String> errorTypes = List.of("TIMEOUT", "QUOTA", "INTERNAL");

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public List<String> getErrorTypes() {
        return errorTypes;
    }

    public void setErrorTypes(List<String> errorTypes) {
        this.errorTypes = errorTypes;
    }
}
