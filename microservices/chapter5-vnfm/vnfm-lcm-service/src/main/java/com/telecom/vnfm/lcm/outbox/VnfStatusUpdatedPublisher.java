package com.telecom.vnfm.lcm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.VnfStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VnfStatusUpdatedPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${vnfm.lcm.outbox.nfvo-destination:nfvo-vnf-notifications}")
    private String nfvoDestination;

    public void publish(VnfStatusUpdatedEvent event) {
        if (event == null) return;
        publish(Collections.singletonList(event));
    }

    public void publish(List<VnfStatusUpdatedEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (VnfStatusUpdatedEvent event : events) {
            outboxMessageRepository.save(OutboxMessageEntity.create(nfvoDestination, toJson(event)));
        }
    }

    private String toJson(VnfStatusUpdatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize VnfStatusUpdatedEvent", e);
        }
    }
}
