package com.telecom.mediation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.mediation.model.CdrEntity;
import com.telecom.mediation.model.CdrProcessedEvent;
import com.telecom.mediation.model.OutboxMessage;
import com.telecom.mediation.model.RawCdr;
import com.telecom.mediation.repository.CdrRepository;
import com.telecom.mediation.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MODULE 3: Core business logic — save CDR and outbox message in ONE transaction.
 *
 * CONCEPT (Transactional Outbox):
 * - We do NOT call Kafka here. If we did, and DB commits but Kafka fails, we get
 *   "Revenue Leakage" (CDR saved, customer never billed). That's the Dual Write Problem.
 * - We INSERT into the "message" (outbox) table in the same @Transactional method
 *   as the CDR insert. Same DB = same transaction = atomicity. The relay (scheduled
 *   job) later reads unpublished rows and sends to Kafka.
 *
 * @Transactional: Spring's declarative transaction management. The method runs
 * inside a DB transaction. If any statement fails or we throw, the whole transaction
 * rolls back. Both cdrRepository.save() and outboxMessageRepository.save() commit
 * together or not at all.
 *
 * ObjectMapper: Jackson; we use it to serialise CdrProcessedEvent to JSON for the
 * outbox payload. The relay (and Billing consumer) will deserialise the same JSON.
 */
@Service
public class CdrMediationService {

    private static final Logger log = LoggerFactory.getLogger(CdrMediationService.class);

    /** Topic/destination for billing events. Matches the Notes "billing-events" or aggregate type. */
    public static final String BILLING_EVENTS_DESTINATION = "billing-events";

    private final CdrRepository cdrRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${mediation.outbox.destination:billing-events}")
    private String outboxDestination;

    public CdrMediationService(CdrRepository cdrRepository,
                               OutboxMessageRepository outboxMessageRepository,
                               ObjectMapper objectMapper) {
        this.cdrRepository = cdrRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomic: save CDR + outbox row. No Kafka call here — that's the whole point.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processCdr(RawCdr rawCdr) {
        // 1. Business data: save CDR for audit
        CdrEntity cdr = new CdrEntity(rawCdr);
        cdrRepository.save(cdr);
        log.debug("Saved CDR id={}", cdr.getId());

        // 2. Event payload (what Billing will consume)
        CdrProcessedEvent event = new CdrProcessedEvent(
                cdr.getId(),
                cdr.getSubscriberId(),
                cdr.getCost()
        );

        // 3. Write to outbox in the SAME transaction — no Kafka yet
        String payloadJson = toJson(event);
        OutboxMessage outbox = new OutboxMessage(
                UUID.randomUUID().toString(),
                outboxDestination,
                "{}",
                payloadJson
        );
        outboxMessageRepository.save(outbox);

        // COMMIT: both INSERTs succeed or both roll back. Relay will later send payload to Kafka.
    }

    private String toJson(CdrProcessedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise billing event", e);
        }
    }
}
