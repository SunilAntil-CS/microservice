package com.vnfm.lcm.domain.model;

/**
 * State of an LCM operation occurrence (ETSI SOL002).
 */
public enum OpOccState {
    STARTING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ROLLING_BACK
}
