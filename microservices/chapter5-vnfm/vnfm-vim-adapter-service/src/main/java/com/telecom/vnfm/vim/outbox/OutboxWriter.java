package com.telecom.vnfm.vim.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;

    public void write(String destinationTopic, String jsonPayload) {
        if (destinationTopic == null || jsonPayload == null) return;
        outboxMessageRepository.save(OutboxMessageEntity.create(destinationTopic, jsonPayload));
    }
}
