package com.telecom.fms.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.fms.model.LinkDownEvent;
import com.telecom.fms.model.ReceivedMessage;
import com.telecom.fms.model.TroubleTicket;
import com.telecom.fms.repository.ReceivedMessageRepository;
import com.telecom.fms.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MODULE 4: Idempotent Consumer. Listens to alarm events (e.g. Link Down); creates
 * ONE trouble ticket per unique message_id. Duplicate deliveries are detected via
 * the received_messages table (same DB transaction as business logic).
 * ------------------------------------------------------------------------------------
 * @KafkaListener(topics = "...", groupId = "..."): Spring Kafka registers this method.
 * When a record arrives on the topic, this method is invoked. We use ConsumerRecord
 * to read record.key() (message_id — producer should set key for idempotency) and
 * record.value() (JSON payload). groupId = consumer group; offsets are committed per group.
 * ------------------------------------------------------------------------------------
 * DataIntegrityViolationException: Thrown by Spring when the DB raises a constraint
 * violation (e.g. duplicate primary key). We catch it and return without creating a
 * second ticket. In some setups you may get PersistenceException or DuplicateKeyException;
 * catch the appropriate type for your JPA/driver.
 * ------------------------------------------------------------------------------------
 * CRITICAL: Idempotency insert and business insert are in ONE @Transactional method.
 * Using Redis for "seen?" and MySQL for ticket would allow a race (crash between check
 * and write). Same DB + same transaction = ACID guard.
 */
@Component
public class AlarmEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmEventConsumer.class);

    /** Consumer identity; same as in Notes (e.g. "FMS-Service"). */
    public static final String CONSUMER_ID = "fms-service";

    private final ReceivedMessageRepository receivedMessageRepository;
    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper;

    public AlarmEventConsumer(ReceivedMessageRepository receivedMessageRepository,
                             TicketRepository ticketRepository,
                             ObjectMapper objectMapper) {
        this.receivedMessageRepository = receivedMessageRepository;
        this.ticketRepository = ticketRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${fms.kafka.topic:alarm-events}", groupId = "${fms.kafka.group-id:fms-group}")
    public void handleAlarmEvent(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        String messageId = record.key();
        if (messageId == null || messageId.isBlank()) {
            messageId = record.offset() + "-" + record.partition();
        }
        String payload = record.value();
        if (payload == null || payload.isBlank()) {
            log.warn("Ignoring empty payload for key={}", messageId);
            return;
        }
        LinkDownEvent event;
        try {
            event = objectMapper.readValue(payload, LinkDownEvent.class);
        } catch (Exception e) {
            log.error("Invalid payload for messageId={}: {}", messageId, e.getMessage());
            return;
        }
        handleLinkDownEvent(messageId, event);
    }

    /**
     * ACID guard: insert idempotency row and business row in ONE transaction.
     * Duplicate key on received_messages → we already processed this message → return.
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleLinkDownEvent(String messageId, LinkDownEvent event) {
        try {
            receivedMessageRepository.save(new ReceivedMessage(CONSUMER_ID, messageId));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate event detected: messageId={}. Ignoring.", messageId);
            return;
        }
        TroubleTicket ticket = new TroubleTicket(
                UUID.randomUUID().toString(),
                event.nodeId(),
                event.linkId(),
                "LINK_DOWN"
        );
        ticketRepository.save(ticket);
        log.info("Created ticket {} for nodeId={} messageId={}", ticket.getId(), event.nodeId(), messageId);
    }
}
