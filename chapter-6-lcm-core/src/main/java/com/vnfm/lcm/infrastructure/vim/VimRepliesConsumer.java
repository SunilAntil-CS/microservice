package com.vnfm.lcm.infrastructure.vim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.lcm.infrastructure.saga.SagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes VIM replies from the {@code vim.replies} topic and forwards them to
 * {@link SagaOrchestrator#handleReply(UUID, int, boolean, Map)} so the saga can
 * advance to the next step, complete, or run compensation.
 */
@Component
public class VimRepliesConsumer {

    private static final Logger log = LoggerFactory.getLogger(VimRepliesConsumer.class);

    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public VimRepliesConsumer(SagaOrchestrator sagaOrchestrator, ObjectMapper objectMapper) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${lcm.vim.replies-topic:vim.replies}",
            groupId = "${spring.kafka.consumer.group-id:lcm-core-group}"
    )
    public void onVimReply(String payload) {
        try {
            VimReplyPayload reply = objectMapper.readValue(payload, VimReplyPayload.class);
            UUID sagaId = UUID.fromString(reply.getSagaId());
            int step = reply.getStep();
            boolean success = reply.isSuccess();
            Map<String, Object> result = reply.getResult() != null ? reply.getResult() : Map.<String, Object>of();
            if (!success && reply.getReason() != null) {
                result = Map.of("reason", reply.getReason());
            }

            sagaOrchestrator.handleReply(sagaId, step, success, result);
            log.debug("Processed VIM reply sagaId={} step={} success={}", sagaId, step, success);
        } catch (Exception e) {
            log.error("Failed to process VIM reply: {}", e.getMessage(), e);
            throw e;
        }
    }
}
