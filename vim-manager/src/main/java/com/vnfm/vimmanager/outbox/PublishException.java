package com.vnfm.vimmanager.outbox;

public class PublishException extends RuntimeException {

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public PublishException(String message) {
        super(message);
    }
}
