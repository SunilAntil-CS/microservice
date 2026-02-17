package com.vnfm.lcm.infrastructure.saga;

/**
 * Status of a saga instance.
 * RUNNING = forward flow in progress; COMPENSATING = rolling back; COMPLETED / FAILED = terminal.
 */
public enum SagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    FAILED
}
