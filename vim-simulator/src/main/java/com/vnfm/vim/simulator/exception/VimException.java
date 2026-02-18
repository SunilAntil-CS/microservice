package com.vnfm.vim.simulator.exception;

/**
 * Thrown when the mock VIM simulates a failure (TIMEOUT, QUOTA, INTERNAL).
 */
public class VimException extends RuntimeException {

    private final String errorType;

    public VimException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
