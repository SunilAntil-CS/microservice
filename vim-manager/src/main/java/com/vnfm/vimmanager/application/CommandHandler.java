package com.vnfm.vimmanager.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.vimmanager.client.VimClient;
import com.vnfm.vimmanager.client.VimRequest;
import com.vnfm.vimmanager.client.VimResponse;
import com.vnfm.vimmanager.domain.ProcessedCommandRepository;
import com.vnfm.vimmanager.domain.command.VimCommandPayload;
import com.vnfm.vimmanager.domain.command.VimReplyPayload;
import com.vnfm.vimmanager.domain.ProcessedCommand;
import com.vnfm.vimmanager.outbox.OutboxMessage;
import com.vnfm.vimmanager.outbox.OutboxRepository;
import com.vnfm.vimmanager.translation.ResourceRequestTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Processes VIM commands (ReserveResources, ReleaseResources): idempotency check,
 * translate to VIM request, call VimClient, write reply to outbox for vim.replies.
 */
@Service
public class CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    public static final String CMD_RESERVE_RESOURCES = "ReserveResources";
    public static final String CMD_RELEASE_RESOURCES = "ReleaseResources";
    public static final String REPLY_DESTINATION = "vim.replies";
    public static final int STEP_RESERVE = 1;
    public static final int STEP_RELEASE = 1; // compensation step

    private final ProcessedCommandRepository processedCommandRepository;
    private final OutboxRepository outboxRepository;
    private final ResourceRequestTranslator translator;
    private final VimClient vimClient;
    private final ObjectMapper objectMapper;

    @Value("${vim.replies-topic:vim.replies}")
    private String repliesTopic;

    public CommandHandler(ProcessedCommandRepository processedCommandRepository,
                          OutboxRepository outboxRepository,
                          ResourceRequestTranslator translator,
                          VimClient vimClient,
                          ObjectMapper objectMapper) {
        this.processedCommandRepository = processedCommandRepository;
        this.outboxRepository = outboxRepository;
        this.translator = translator;
        this.vimClient = vimClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle a command from vim.commands. Idempotent: skips if messageId already processed.
     *
     * @param messageId  unique message id (for idempotency)
     * @param messageType ReserveResources or ReleaseResources
     * @param payloadJson JSON payload (sagaId, vnfId, resources, etc.)
     * @return true if processed (or already seen), false if payload invalid
     */
    @Transactional
    public boolean handle(String messageId, String messageType, String payloadJson) {
        if (processedCommandRepository.existsByMessageId(messageId)) {
            log.debug("Ignoring duplicate command messageId={}", messageId);
            return true;
        }

        VimCommandPayload payload;
        try {
            payload = objectMapper.readValue(payloadJson, VimCommandPayload.class);
        } catch (Exception e) {
            log.warn("Invalid payload for messageId={}: {}", messageId, e.getMessage());
            return false;
        }

        // Claim messageId before slow VIM call so duplicate deliveries (or concurrent consumers)
        // see this message as already processed and skip. If another thread already inserted,
        // we get DataIntegrityViolationException (unique constraint) and treat as idempotent.
        try {
            processedCommandRepository.saveAndFlush(new ProcessedCommand(messageId));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate command messageId={} (already claimed or processed)", messageId);
            return true;
        }

        int step = CMD_RESERVE_RESOURCES.equals(messageType) ? STEP_RESERVE : STEP_RELEASE;
        VimRequest request = translator.toVimRequest(messageType, payload);
        VimResponse response = vimClient.execute(request);

        VimReplyPayload reply = response.isSuccess()
                ? VimReplyPayload.success(payload.getSagaId(), step, response.getResult())
                : VimReplyPayload.failure(payload.getSagaId(), step, response.getErrorMessage());

        String replyType = response.isSuccess() ? "VimReplySuccess" : "VimReplyFailure";
        String replyPayloadJson;
        try {
            replyPayloadJson = objectMapper.writeValueAsString(reply);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize reply", e);
        }

        OutboxMessage outbox = new OutboxMessage(
                UUID.randomUUID().toString(),
                repliesTopic,
                replyType,
                replyPayloadJson
        );
        outboxRepository.save(outbox);
        log.info("Processed command messageId={} type={} sagaId={} success={}", messageId, messageType, payload.getSagaId(), response.isSuccess());
        return true;
    }
}
