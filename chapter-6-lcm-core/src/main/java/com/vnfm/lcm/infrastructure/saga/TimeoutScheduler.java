package com.vnfm.lcm.infrastructure.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs periodically, finds saga timeouts that are due (executeAt <= now and not processed),
 * and for each: if the saga is still waiting for that step, triggers compensation via handleReply(failure);
 * then marks the timeout as processed so it is not handled again.
 */
@Component
public class TimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutScheduler.class);

    private final SagaTimeoutRepository sagaTimeoutRepository;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public TimeoutScheduler(SagaTimeoutRepository sagaTimeoutRepository,
                            SagaInstanceRepository sagaInstanceRepository,
                            SagaOrchestrator sagaOrchestrator) {
        this.sagaTimeoutRepository = sagaTimeoutRepository;
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Scheduled(fixedDelayString = "${lcm.saga.timeout-scheduler.fixed-delay:5000}")
    @Transactional
    public void processDueTimeouts() {
        Instant now = Instant.now();
        List<SagaTimeout> due = sagaTimeoutRepository.findByProcessedFalseAndExecuteAtLessThanEqualOrderByExecuteAtAsc(now);

        for (SagaTimeout timeout : due) {
            try {
                sagaInstanceRepository.findBySagaId(timeout.getSagaId()).ifPresentOrElse(
                        saga -> {
                            if (saga.getStatus() == SagaStatus.RUNNING && saga.getCurrentStep() == timeout.getStep()) {
                                log.info("Saga {} step {} timed out; triggering compensation", timeout.getSagaId(), timeout.getStep());
                                sagaOrchestrator.handleReply(
                                        UUID.fromString(timeout.getSagaId()),
                                        timeout.getStep(),
                                        false,
                                        Map.of("reason", "timeout")
                                );
                            } else {
                                sagaOrchestrator.markTimeoutProcessed(timeout.getSagaId(), timeout.getStep());
                            }
                        },
                        () -> {
                            sagaOrchestrator.markTimeoutProcessed(timeout.getSagaId(), timeout.getStep());
                        }
                );
            } catch (Exception e) {
                log.error("Error processing timeout sagaId={} step={}: {}", timeout.getSagaId(), timeout.getStep(), e.getMessage(), e);
                timeout.setProcessed(true);
                sagaTimeoutRepository.save(timeout);
            }
        }
    }
}
