package com.telecom.saga.order.saga;

/**
 * Thrown when a saga participant returns an error or times out.
 * The orchestrator uses this to trigger compensation (undo) of previous steps.
 */
public class SagaStepFailedException extends RuntimeException {

    public SagaStepFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SagaStepFailedException(String message) {
        super(message);
    }
}
