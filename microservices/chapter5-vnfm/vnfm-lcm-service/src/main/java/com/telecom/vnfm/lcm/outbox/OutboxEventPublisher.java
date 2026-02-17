package com.telecom.vnfm.lcm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
            outboxMessageRepository.save(OutboxMessageEntity.create(destination, payload));
        }
    }

    private String toJson(InfraDeploymentRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
