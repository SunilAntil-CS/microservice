package com.vnfm.lcm.domain.model;

/**
 * Type of LCM operation (ETSI SOL002).
 */
public enum LcmOperationType {
    INSTANTIATE,
    TERMINATE,
    SCALE,
    HEAL,
    CHANGE_EXT_CONN,
    CHANGE_FLAVOUR,
    OPERATE
}
