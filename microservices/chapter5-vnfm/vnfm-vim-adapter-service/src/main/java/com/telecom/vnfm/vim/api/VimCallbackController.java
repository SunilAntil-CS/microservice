package com.telecom.vnfm.vim.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.vnfm.common.event.InfraDeployedReplyEvent;
import com.telecom.vnfm.common.event.InfraDeploymentFailedEvent;
import com.telecom.vnfm.common.event.InfraDeploymentProgressEvent;
import com.telecom.vnfm.vim.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

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
        if (dto == null || dto.getType() == null) return ResponseEntity.badRequest().build();
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
                return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private void writeProgress(VimWebhookDto dto) {
        outboxWriter.write(topicProgress, toJson(InfraDeploymentProgressEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .progressMessage(dto.getProgressMessage())
                .progressPercent(dto.getProgressPercent())
                .build()));
    }

    private void writeSuccess(VimWebhookDto dto) {
        outboxWriter.write(topicReply, toJson(InfraDeployedReplyEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .status(InfraDeployedReplyEvent.STATUS_SUCCESS)
                .build()));
    }

    private void writeFailure(VimWebhookDto dto) {
        outboxWriter.write(topicFailed, toJson(InfraDeploymentFailedEvent.builder()
                .vnfId(dto.getVnfId())
                .deploymentId(dto.getDeploymentId())
                .reason(dto.getReason())
                .errorCode(dto.getErrorCode())
                .build()));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
