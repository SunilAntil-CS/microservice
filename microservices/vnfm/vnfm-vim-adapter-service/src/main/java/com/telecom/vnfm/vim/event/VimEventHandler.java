package com.telecom.vnfm.vim.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.InfraDeploymentAcceptedEvent;
import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import com.telecom.vnfm.vim.client.VimSimulatorClient;
import com.telecom.vnfm.vim.domain.CloudDeployment;
import com.telecom.vnfm.vim.domain.CloudDeploymentRepository;
import com.telecom.vnfm.vim.idempotency.ProcessedMessageEntity;
import com.telecom.vnfm.vim.idempotency.ProcessedMessageRepository;
import com.telecom.vnfm.vim.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.validation.Valid;
import java.util.Optional;

/**
 * IDEMPOTENT KAFKA LISTENER: Consumes InfraDeploymentRequestedEvent (LCM → VIM).
 * ---------------------------------------------------------------------------
 * 1. Claims message_id in processed_messages (Idempotent Consumer). Duplicates
 *    are rejected by PK constraint; we ack and return.
 * 2. Creates CloudDeployment in CREATING state.
 * 3. Writes InfraDeploymentAcceptedEvent to the outbox (same TX)—ACK to LCM.
 * 4. Commits; then invokes VimSimulatorClient.deployAsync() so the simulator
 *    is called outside the transaction. The simulator will callback to our
 *    webhook with Progress and then Success/Failure; we write those to the
 *    outbox in the webhook controller.
 *
 * Manual ack: we acknowledge only after the transaction commits (safeAcknowledge).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VimEventHandler {

    private final CloudDeploymentRepository cloudDeploymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final VimSimulatorClient vimSimulatorClient;

    @Value("${vnfm.vim.outbox.topic.accepted:infra-deployment-accepted}")
    private String topicAccepted;

    private void safeAcknowledge(Acknowledgment ack) {
        if (ack == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(
            topics = "${vnfm.vim.topic:infra-deployment-requested}",
            groupId = "${vnfm.vim.consumer-group:vim-adapter}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInfraDeploymentRequested(
            @Payload @Valid InfraDeploymentRequestedEvent event,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {

        if (event == null) {
            log.warn("Received null event; skipping");
            safeAcknowledge(ack);
            return;
        }

        String eventId = Optional.ofNullable(messageId).orElse(messageIdAlt);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Fatal: Missing message_id header");
        }

        if (processedMessageRepository.existsById(eventId)) {
            log.info("Duplicate message ignored: eventId={}, vnfId={}", eventId, event.getVnfId());
            safeAcknowledge(ack);
            return;
        }

        try {
            processedMessageRepository.saveAndFlush(new ProcessedMessageEntity(eventId));
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate rejected by Idempotency Shield: eventId={}, vnfId={}", eventId, event.getVnfId());
            safeAcknowledge(ack);
            return;
        }

        CloudDeployment deployment = CloudDeployment.startDeployment(event.getVnfId());
        deployment = cloudDeploymentRepository.save(deployment);

        InfraDeploymentAcceptedEvent ackEvent = InfraDeploymentAcceptedEvent.builder()
                .vnfId(event.getVnfId())
                .deploymentId(deployment.getDeploymentId())
                .message("Request accepted, deployment queued")
                .build();
        outboxWriter.write(topicAccepted, toJson(ackEvent));

        log.info("Cloud deployment created (ACK sent): deploymentId={}, vnfId={}, eventId={}",
                deployment.getDeploymentId(), event.getVnfId(), eventId);

        safeAcknowledge(ack);

        vimSimulatorClient.deployAsync(event, deployment.getDeploymentId());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
