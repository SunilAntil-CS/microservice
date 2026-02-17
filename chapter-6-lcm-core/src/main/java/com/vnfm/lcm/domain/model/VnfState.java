package com.vnfm.lcm.domain.model;

/**
 * Lifecycle state of a VNF aggregate.
 */
public enum VnfState {
    /** Aggregate created; no instantiation started yet. */
    INITIAL,
    /** Instantiation requested; waiting for VIM to allocate resources. */
    INSTANTIATING,
    /** VNF is running and reachable. */
    ACTIVE,
    /** Termination requested; resources being released. */
    TERMINATING,
    /** VNF has been fully removed. */
    TERMINATED,
    /** Instantiation or termination failed. */
    FAILED
}
