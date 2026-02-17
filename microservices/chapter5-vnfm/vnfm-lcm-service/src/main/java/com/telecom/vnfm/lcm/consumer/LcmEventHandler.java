package com.telecom.vnfm.lcm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.*;
import com.telecom.vnfm.lcm.idempotency.ProcessedMessageEntity;
import com.telecom.vnfm.lcm.idempotency.ProcessedMessageRepository;
import com.telecom.vnfm.lcm.service.LcmService;
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

import java.util.Optional;

/** Idempotent consumer for VIM events (ACK, Progress, Reply, Failed). Manual ack after commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LcmEventHandler {

    private final LcmService lcmService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    private void safeAcknowledge(Acknowledgment ack) {
        if (ack == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
            }
        });
    }

    private boolean tryClaimMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("Missing message_id header");
        if (processedMessageRepository.existsById(messageId)) return false;
        try {
            processedMessageRepository.saveAndFlush(ProcessedMessageEntity.of(messageId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private String messageId(@Header(value = "message_id", required = false) String a,
                            @Header(value = "id", required = false) String b) {
        return Optional.ofNullable(a).orElse(b);
    }

    private <T> T parse(String payload, Class<T> type) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize to {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.accepted:infra-deployment-accepted}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentAccepted(@Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        if (!tryClaimMessage(messageId(messageId, messageIdAlt))) { safeAcknowledge(ack); return; }
        InfraDeploymentAcceptedEvent event = parse(payload, InfraDeploymentAcceptedEvent.class);
        if (event != null) log.info("VIM ACK: vnfId={}, deploymentId={}", event.getVnfId(), event.getDeploymentId());
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.progress:infra-deployment-progress}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentProgress(@Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        if (!tryClaimMessage(messageId(messageId, messageIdAlt))) { safeAcknowledge(ack); return; }
        InfraDeploymentProgressEvent event = parse(payload, InfraDeploymentProgressEvent.class);
        if (event != null) log.info("VIM Progress: vnfId={}, {}%", event.getVnfId(), event.getProgressPercent());
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.reply:infra-deployed-reply}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeployedReply(@Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        if (!tryClaimMessage(messageId(messageId, messageIdAlt))) { safeAcknowledge(ack); return; }
        InfraDeployedReplyEvent event = parse(payload, InfraDeployedReplyEvent.class);
        if (event == null) { safeAcknowledge(ack); return; }
        if (InfraDeployedReplyEvent.STATUS_SUCCESS.equals(event.getStatus())) {
            lcmService.markInfraDeployed(event.getVnfId(), event.getDeploymentId());
        }
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.failed:infra-deployment-failed}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentFailed(@Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        if (!tryClaimMessage(messageId(messageId, messageIdAlt))) { safeAcknowledge(ack); return; }
        InfraDeploymentFailedEvent event = parse(payload, InfraDeploymentFailedEvent.class);
        if (event == null) { safeAcknowledge(ack); return; }
        String reason = event.getReason() != null ? event.getReason() : event.getErrorCode();
        lcmService.markInfraFailed(event.getVnfId(), reason != null ? reason : "VIM reported failure");
        safeAcknowledge(ack);
    }
}
