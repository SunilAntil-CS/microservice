package com.cqrs.policyengine.application;

import com.cqrs.policyengine.domain.PolicyEvent;
import com.cqrs.policyengine.infrastructure.outbox.OutboxEntry;
import com.cqrs.policyengine.infrastructure.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mock policy engine: generates random policy events and writes them to the outbox table
 * in a single transaction. Debezium captures outbox changes and publishes to Kafka.
 */
@Component
public class PolicyEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(PolicyEventEmitter.class);
    private static final String EVENT_TYPE = "PolicyEvent";
    private static final List<String> SUBSCRIBER_IDS = List.of("sub-001", "sub-002", "sub-003");
    private static final List<String> POLICY_NAMES = List.of("QuotaPolicy", "RateLimitPolicy", "AccessPolicy");

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PolicyEventEmitter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${policy-engine.emit-interval-ms:5000}")
    @Transactional
    public void emitRandomEvent() {
        PolicyEvent event = new PolicyEvent(
                UUID.randomUUID(),
                randomOf(SUBSCRIBER_IDS),
                Instant.now(),
                randomOf(POLICY_NAMES),
                Math.random() > 0.3,
                (long) (Math.random() * 1000)
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize policy event", e);
        }

        OutboxEntry entry = new OutboxEntry(
                event.eventId(),
                event.subscriberId(),
                EVENT_TYPE,
                payload,
                event.timestamp()
        );
        outboxRepository.save(entry);
        log.info("Emitted policy event eventId={} subscriberId={} policyName={} decision={}",
                event.eventId(), event.subscriberId(), event.policyName(), event.decision());
    }

    private static String randomOf(List<String> list) {
        return list.get((int) (Math.random() * list.size()));
    }
}
