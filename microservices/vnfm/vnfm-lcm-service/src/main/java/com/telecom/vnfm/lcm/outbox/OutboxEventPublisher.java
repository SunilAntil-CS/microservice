package com.telecom.vnfm.lcm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Transactional Outbox implementation of DomainEventPublisher.
 * ---------------------------------------------------------------------------
 * This class does NOT send to Kafka. It only writes to the outbox table in the
 * same transaction as the aggregate. The actual Kafka send is done by
 * OutboxRelay (scheduled job), which reads unpublished rows and calls
 * kafkaTemplate.send(...). See OutboxRelay.sendToKafka() and publishUnpublished().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements DomainEventPublisher<InfraDeploymentRequestedEvent> {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${vnfm.lcm.outbox.destination:infra-deployment-requested}")
    private String destination;

    @Override
    public void publish(Class<?> aggregateType, Object aggregateId, List<InfraDeploymentRequestedEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (InfraDeploymentRequestedEvent event : events) {
            String payload = toJson(event);
            OutboxMessageEntity message = OutboxMessageEntity.create(destination, payload);
            outboxMessageRepository.save(message);
            log.debug("Outbox enqueued: aggregateId={}, messageId={}", aggregateId, message.getId());
        }
    }

    private String toJson(InfraDeploymentRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize domain event", e);
        }
    }
}
