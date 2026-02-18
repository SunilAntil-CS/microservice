package com.vnfm.vimmanager.outbox;

public interface MessagePublisher {

    void publish(OutboxMessage message);
}
