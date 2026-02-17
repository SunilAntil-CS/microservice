package com.telecom.vnfm.lcm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.VnfStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Publishes VnfStatusUpdatedEvent to the Transactional Outbox (LCM → NFVO).
 * ---------------------------------------------------------------------------
 * Whenever the LCM service changes a VnfInstance's state, it writes a
 * VnfStatusUpdatedEvent to the same outbox table with destination
 * {@code nfvo-vnf-notifications}. The OutboxRelay then publishes to Kafka;
 * the NFVO (or simulator) consumes and updates its dashboard.
 *
 * WHY A SEPARATE PUBLISHER?
 * - The LCM emits two event types: InfraDeploymentRequestedEvent (to VIM) and
 *   VnfStatusUpdatedEvent (to NFVO). Both use the same outbox table and relay;
 *   only the destination topic differs. This component handles the NFVO-bound events.
 */
@Slf4j
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
            String payload = toJson(event);
            OutboxMessageEntity message = OutboxMessageEntity.create(nfvoDestination, payload);
            outboxMessageRepository.save(message);
            log.debug("Outbox enqueued VnfStatusUpdated: vnfId={}, state={}, messageId={}",
                    event.getVnfId(), event.getState(), message.getId());
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
