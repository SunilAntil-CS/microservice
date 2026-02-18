package com.vnfm.vimmanager.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job: poll outbox for PENDING messages and publish to Kafka (vim.replies).
 */
@Component
public class OutboxForwarder {

    private static final Logger log = LoggerFactory.getLogger(OutboxForwarder.class);
    private static final int BASE_DELAY_SECONDS = 2;

    private final OutboxRepository outboxRepository;
    private final MessagePublisher messagePublisher;

    public OutboxForwarder(OutboxRepository outboxRepository, MessagePublisher messagePublisher) {
        this.outboxRepository = outboxRepository;
        this.messagePublisher = messagePublisher;
    }

    @Scheduled(fixedDelayString = "${vim.outbox.forwarder.fixed-delay:5000}")
    @Transactional
    public void forward() {
        Instant now = Instant.now();
        List<OutboxMessage> due = outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OutboxStatus.PENDING, now);

        for (OutboxMessage message : due) {
            try {
                messagePublisher.publish(message);
                message.setStatus(OutboxStatus.SENT);
                message.setLastError(null);
                outboxRepository.save(message);
                log.debug("Published outbox message id={} destination={}", message.getMessageId(), message.getDestination());
            } catch (PublishException e) {
                int retryCount = message.getRetryCount() + 1;
                message.setRetryCount(retryCount);
                message.setLastError(e.getMessage());
                long delaySeconds = (long) (BASE_DELAY_SECONDS * Math.pow(2, retryCount));
                message.setNextRetryAt(now.plusSeconds(delaySeconds));
                outboxRepository.save(message);
                log.warn("Publish failed for message id={}, retryCount={}: {}", message.getMessageId(), retryCount, e.getMessage());
            }
        }
    }
}
