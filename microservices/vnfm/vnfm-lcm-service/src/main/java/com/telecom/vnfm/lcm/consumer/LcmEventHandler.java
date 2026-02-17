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

/**
 * IDEMPOTENT KAFKA LISTENER: Consumes the 3-Event Lifecycle (and ACK) from VIM.
 * ---------------------------------------------------------------------------
 * Listens to: infra-deployment-accepted (ACK), infra-deployment-progress (Progress),
 * infra-deployed-reply (Terminal Success), infra-deployment-failed (Terminal Failure).
 *
 * WHY IDEMPOTENT CONSUMER? Kafka gives at-least-once delivery. The same message
 * can be redelivered after a rebalance or broker retry. If we processed it twice,
 * we would double-apply (e.g. mark ACTIVE twice, or run compensation twice). The
 * processed_messages table is our "bouncer": we try to INSERT the message_id first.
 * PK constraint ensures only one consumer wins; duplicates get DataIntegrityViolation
 * and we ack without re-running business logic.
 *
 * WHY MANUAL ACK? We call Acknowledgment.acknowledge() only after the transaction
 * commits (safeAcknowledge). If we acked before commit and then crashed, we would
 * lose the message (offset advanced but state not persisted). Delayed ack keeps
 * at-least-once semantics correct.
 *
 * COMPENSATION: When InfraDeploymentFailedEvent is received, we call
 * lcmService.markInfraFailed(vnfId, reason). The LCM aggregate transitions to
 * FAILED and emits VnfStatusUpdatedEvent to the NFVO—the rollback path of the Saga.
 */
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
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Fatal: Missing message_id header");
        }
        if (processedMessageRepository.existsById(messageId)) {
            return false;
        }
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

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.accepted:infra-deployment-accepted}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentAccepted(
            @Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        String id = messageId(messageId, messageIdAlt);
        if (!tryClaimMessage(id)) {
            log.debug("Duplicate ACK ignored: messageId={}", id);
            safeAcknowledge(ack);
            return;
        }
        InfraDeploymentAcceptedEvent event = parse(payload, InfraDeploymentAcceptedEvent.class);
        if (event != null) {
            log.info("VIM ACK received: vnfId={}, deploymentId={}, message={}",
                    event.getVnfId(), event.getDeploymentId(), event.getMessage());
        }
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.progress:infra-deployment-progress}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentProgress(
            @Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        String id = messageId(messageId, messageIdAlt);
        if (!tryClaimMessage(id)) {
            log.debug("Duplicate Progress ignored: messageId={}", id);
            safeAcknowledge(ack);
            return;
        }
        InfraDeploymentProgressEvent event = parse(payload, InfraDeploymentProgressEvent.class);
        if (event != null) {
            log.info("VIM Progress: vnfId={}, deploymentId={}, progress={}%",
                    event.getVnfId(), event.getDeploymentId(), event.getProgressPercent());
        }
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.reply:infra-deployed-reply}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeployedReply(
            @Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        String id = messageId(messageId, messageIdAlt);
        if (!tryClaimMessage(id)) {
            log.info("Duplicate reply ignored: messageId={}", id);
            safeAcknowledge(ack);
            return;
        }
        InfraDeployedReplyEvent event = parse(payload, InfraDeployedReplyEvent.class);
        if (event == null) {
            log.warn("Received null reply event; skipping");
            safeAcknowledge(ack);
            return;
        }
        if (InfraDeployedReplyEvent.STATUS_SUCCESS.equals(event.getStatus())) {
            lcmService.markInfraDeployed(event.getVnfId(), event.getDeploymentId());
        } else {
            log.error("Reply event status not SUCCESS: vnfId={}, status={}", event.getVnfId(), event.getStatus());
        }
        log.info("Reply processed: vnfId={}, deploymentId={}, messageId={}", event.getVnfId(), event.getDeploymentId(), id);
        safeAcknowledge(ack);
    }

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(topics = "${vnfm.lcm.topic.failed:infra-deployment-failed}",
            groupId = "${vnfm.lcm.consumer.group-id:lcm-service-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInfraDeploymentFailed(
            @Payload String payload,
            @Header(value = "message_id", required = false) String messageId,
            @Header(value = "id", required = false) String messageIdAlt,
            Acknowledgment ack) {
        String id = messageId(messageId, messageIdAlt);
        if (!tryClaimMessage(id)) {
            log.debug("Duplicate Failed ignored: messageId={}", id);
            safeAcknowledge(ack);
            return;
        }
        InfraDeploymentFailedEvent event = parse(payload, InfraDeploymentFailedEvent.class);
        if (event == null) {
            log.warn("Received null failed event; skipping");
            safeAcknowledge(ack);
            return;
        }
        String reason = event.getReason() != null ? event.getReason() : event.getErrorCode();
        lcmService.markInfraFailed(event.getVnfId(), reason != null ? reason : "VIM reported failure");
        log.warn("Infra deployment failed (compensation applied): vnfId={}, reason={}", event.getVnfId(), reason);
        safeAcknowledge(ack);
    }

    private <T> T parse(String payload, Class<T> type) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize payload to {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
