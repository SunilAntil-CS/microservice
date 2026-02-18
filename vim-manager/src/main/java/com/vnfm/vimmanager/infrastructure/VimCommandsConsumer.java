package com.vnfm.vimmanager.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.vimmanager.application.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for vim.commands. Delegates to CommandHandler (idempotent processing).
 * Accepts either: (1) wrapper {"messageType":"ReserveResources","payload":{...}} or
 * (2) raw payload with message_type header, or (3) raw payload and infer type (reason -> ReleaseResources).
 */
@Component
public class VimCommandsConsumer {

    private static final Logger log = LoggerFactory.getLogger(VimCommandsConsumer.class);

    private final CommandHandler commandHandler;
    private final ObjectMapper objectMapper;

    public VimCommandsConsumer(CommandHandler commandHandler, ObjectMapper objectMapper) {
        this.commandHandler = commandHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${vim.commands-topic:vim.commands}",
            groupId = "${spring.kafka.consumer.group-id:vim-manager-group}"
    )
    public void onCommand(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageId,
            @Header(value = "message_type", required = false) String messageTypeHeader) {
        String messageIdToUse = messageId != null ? messageId : "unknown";
        String messageType;
        String payloadJson;
        try {
            var root = objectMapper.readTree(payload);
            if (root.has("messageType") && root.has("payload")) {
                messageType = root.get("messageType").asText();
                payloadJson = root.get("payload").isTextual() ? root.get("payload").asText() : root.get("payload").toString();
            } else {
                messageType = messageTypeHeader != null ? messageTypeHeader : inferMessageType(payload);
                payloadJson = payload;
            }
        } catch (Exception e) {
            log.debug("Parse command value as raw payload: {}", e.getMessage());
            messageType = messageTypeHeader != null ? messageTypeHeader : inferMessageType(payload);
            payloadJson = payload;
        }
        try {
            commandHandler.handle(messageIdToUse,  , payloadJson);
        } catch (Exception e) {
            log.error("Error processing command messageId={}", messageIdToUse, e);
            throw e;
        }
    }

    private String inferMessageType(String payload) {
        try {
            var root = objectMapper.readTree(payload);
            if (root.has("reason") && !root.has("resources")) {
                return CommandHandler.CMD_RELEASE_RESOURCES;
            }
        } catch (Exception ignored) { }
        return CommandHandler.CMD_RESERVE_RESOURCES;
    }
}
