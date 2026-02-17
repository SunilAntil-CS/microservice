package com.telecom.vnfm.simulator.nfvo.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.VnfStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NfvoVnfNotificationListener {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${vnfm.nfvo.topic:nfvo-vnf-notifications}",
            groupId = "${vnfm.nfvo.consumer-group:nfvo-simulator}"
    )
    public void onVnfStatusUpdated(String payload) {
        if (payload == null || payload.isBlank()) return;
        try {
            VnfStatusUpdatedEvent event = objectMapper.readValue(payload, VnfStatusUpdatedEvent.class);
            log.info("NFVO Dashboard Updated: VNF {} is now {}", event.getVnfId(), event.getState());
            if (event.getMessage() != null && !event.getMessage().isBlank()) {
                log.info("  Message: {}", event.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to parse VnfStatusUpdatedEvent: {}", e.getMessage());
        }
    }
}
