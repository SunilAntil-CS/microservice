package com.telecom.vnfm.vim.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.InfraDeployedReplyEvent;
import com.telecom.vnfm.common.event.InfraDeploymentFailedEvent;
import com.telecom.vnfm.common.event.InfraDeploymentProgressEvent;
import com.telecom.vnfm.vim.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * WEBHOOK CONTROLLER: Receives callbacks from the VIM Simulator.
 * ---------------------------------------------------------------------------
 * The simulator (OpenStack mock) calls this after its async deploy: first a
 * Progress webhook, then Success or Failure. We translate the raw cloud payload
 * into our domain events and write them to the Transactional Outbox. The
 * OutboxRelay will publish to Kafka; the LCM service consumes and updates
 * VnfInstance state (or runs compensation on Failure).
 */
@Slf4j
@RestController
@RequestMapping("/api/vim")
@RequiredArgsConstructor
public class VimCallbackController {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Value("${vnfm.vim.outbox.topic.progress:infra-deployment-progress}")
    private String topicProgress;
    @Value("${vnfm.vim.outbox.topic.reply:infra-deployed-reply}")
    private String topicReply;
    @Value("${vnfm.vim.outbox.topic.failed:infra-deployment-failed}")
    private String topicFailed;

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@Valid @RequestBody VimWebhookDto dto) {
        if (dto == null || dto.getType() == null) {
            return ResponseEntity.badRequest().build();
        }
        switch (dto.getType().toUpperCase()) {
            case VimWebhookDto.TYPE_PROGRESS:
                writeProgress(dto);
                break;
            case VimWebhookDto.TYPE_SUCCESS:
                writeSuccess(dto);
                break;
            case VimWebhookDto.TYPE_FAILURE:
                writeFailure(dto);
                break;
            default:
                log.warn("Unknown webhook type: {}", dto.getType());
                return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private void writeProgress(VimWebhookDto dto) {
        InfraDeploymentProgressEvent event = InfraDeploymentProgressEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .progressMessage(dto.getProgressMessage())
                .progressPercent(dto.getProgressPercent())
                .build();
        outboxWriter.write(topicProgress, toJson(event));
        log.info("Webhook Progress written to outbox: vnfId={}, deploymentId={}", dto.getVnfId(), dto.getDeploymentId());
    }

    private void writeSuccess(VimWebhookDto dto) {
        InfraDeployedReplyEvent event = InfraDeployedReplyEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .status(InfraDeployedReplyEvent.STATUS_SUCCESS)
                .build();
        outboxWriter.write(topicReply, toJson(event));
        log.info("Webhook Success written to outbox: vnfId={}, deploymentId={}", dto.getVnfId(), dto.getDeploymentId());
    }

    private void writeFailure(VimWebhookDto dto) {
        InfraDeploymentFailedEvent event = InfraDeploymentFailedEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .reason(dto.getReason())
                .errorCode(dto.getErrorCode())
                .build();
        outboxWriter.write(topicFailed, toJson(event));
        log.warn("Webhook Failure written to outbox: vnfId={}, reason={}", dto.getVnfId(), dto.getReason());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
