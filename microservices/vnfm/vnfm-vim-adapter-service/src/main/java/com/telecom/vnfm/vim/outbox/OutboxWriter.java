package com.telecom.vnfm.vim.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generic writer for the Transactional Outbox (VIM Adapter).
 * ---------------------------------------------------------------------------
 * Writes (destination topic, JSON payload) to the outbox table. Used for
 * InfraDeploymentAcceptedEvent, InfraDeploymentProgressEvent, InfraDeployedReplyEvent,
 * and InfraDeploymentFailedEvent. The OutboxRelay publishes to Kafka using
 * the destination column as the topic name. Callers are responsible for
 * serializing the payload to JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;

    /**
     * Writes a message to the outbox. Must be called within a @Transactional
     * method so it commits with the rest of the use case (e.g. CloudDeployment save).
     */
    public void write(String destinationTopic, String jsonPayload) {
        if (destinationTopic == null || jsonPayload == null) return;
        OutboxMessageEntity message = OutboxMessageEntity.create(destinationTopic, jsonPayload);
        outboxMessageRepository.save(message);
        log.debug("Outbox enqueued: destination={}, messageId={}", destinationTopic, message.getId());
    }
}
