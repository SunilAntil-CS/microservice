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

@Slf4j
@Component
@RequiredArgsConstructor
public class VimEventHandler {

    private final CloudDeploymentRepository cloudDeploymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final VimSimulatorClient vimSimulatorClient;

    private static final String TOPIC_ACCEPTED = "infra-deployment-accepted";

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
    @KafkaListener(topics = "${vnfm.vim.topic:infra-deployment-requested}",
            groupId = "${vnfm.vim.consumer-group:vim-adapter}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentRequested(
            @Payload @Valid InfraDeploymentRequestedEvent event,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {

        if (event == null) {
            safeAcknowledge(ack);
            return;
        }
        String eventId = Optional.ofNullable(messageId).orElse(messageIdAlt);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Missing message_id header");
        }
        if (processedMessageRepository.existsById(eventId)) {
            log.info("Duplicate ignored: eventId={}, vnfId={}", eventId, event.getVnfId());
            safeAcknowledge(ack);
            return;
        }
        try {
            processedMessageRepository.saveAndFlush(new ProcessedMessageEntity(eventId));
        } catch (DataIntegrityViolationException e) {
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
        outboxWriter.write(TOPIC_ACCEPTED, toJson(ackEvent));

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
