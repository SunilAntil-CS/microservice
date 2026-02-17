package com.vnfm.lcm.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * STUDY NOTE – Outbox forwarder (scheduled publisher)
 * ---------------------------------------------------
 * Runs every 5 seconds, loads all PENDING messages with nextRetryAt <= now(),
 * and attempts to publish each via MessagePublisher. On success: status = SENT.
 * On failure: increment retryCount, set lastError, set nextRetryAt with exponential
 * backoff (e.g. 2^retryCount seconds) so we don't hammer the broker.
 *
 * @Scheduled: Spring's scheduling; need @EnableScheduling on the application or a config class.
 * @Transactional: each forward run is in a transaction so we can update rows consistently.
 */
@Component
public class OutboxForwarder {

    private static final Logger log = LoggerFactory.getLogger(OutboxForwarder.class);

    /** Base delay in seconds for exponential backoff: nextRetryAt = now + baseDelaySeconds * 2^retryCount */
    private static final int BASE_DELAY_SECONDS = 2;

    private final OutboxRepository outboxRepository;
    private final MessagePublisher messagePublisher;

    public OutboxForwarder(OutboxRepository outboxRepository, MessagePublisher messagePublisher) {
        this.outboxRepository = outboxRepository;
        this.messagePublisher = messagePublisher;
    }

    @Scheduled(fixedDelayString = "${lcm.outbox.forwarder.fixed-delay:5000}")
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
                // STUDY NOTE: Exponential backoff – delay = BASE_DELAY_SECONDS * 2^retryCount
                long delaySeconds = (long) (BASE_DELAY_SECONDS * Math.pow(2, retryCount));
                message.setNextRetryAt(now.plusSeconds(delaySeconds));
                outboxRepository.save(message);
                log.warn("Publish failed for message id={}, retryCount={}, nextRetryAt={}: {}",
                        message.getMessageId(), retryCount, message.getNextRetryAt(), e.getMessage());
            }
        }
    }
}
