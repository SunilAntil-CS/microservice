package com.vnfm.lcm.infrastructure.saga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for persistent saga timeouts: when no reply arrives before executeAt,
 * the TimeoutScheduler triggers compensation and marks the timeout as processed.
 * Uses in-memory H2 and a short timeout (1 second) so the test runs quickly.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "lcm.saga.step-timeout-seconds=1"
})
class SagaTimeoutIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaInstanceRepository sagaInstanceRepository;

    @Autowired
    private SagaTimeoutRepository sagaTimeoutRepository;

    @Autowired
    private TimeoutScheduler timeoutScheduler;

    @Test
    void whenNoReplyBeforeTimeout_schedulerTriggersCompensationAndMarksTimeoutProcessed() throws InterruptedException {
        String vnfId = "vnf-timeout-" + System.currentTimeMillis();
        UUID sagaId = sagaOrchestrator.startInstantiateSaga(vnfId, Map.of("cpu", 2, "memory", 4));

        SagaInstance saga = sagaInstanceRepository.findBySagaId(sagaId.toString()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(saga.getCurrentStep()).isEqualTo(1);

        var timeoutsBefore = sagaTimeoutRepository.findBySagaIdAndStepAndProcessedFalse(sagaId.toString(), 1);
        assertThat(timeoutsBefore).hasSize(1);
        assertThat(timeoutsBefore.get(0).isProcessed()).isFalse();

        Thread.sleep(1500);

        timeoutScheduler.processDueTimeouts();

        saga = sagaInstanceRepository.findBySagaId(sagaId.toString()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);

        var timeoutsAfter = sagaTimeoutRepository.findBySagaIdAndStepAndProcessedFalse(sagaId.toString(), 1);
        assertThat(timeoutsAfter).isEmpty();
        var allForSaga = sagaTimeoutRepository.findAll().stream()
                .filter(t -> t.getSagaId().equals(sagaId.toString()))
                .toList();
        assertThat(allForSaga).hasSize(1);
        assertThat(allForSaga.get(0).isProcessed()).isTrue();
    }

    @Test
    void whenReplyArrivesBeforeTimeout_timeoutIsMarkedProcessedAndSchedulerDoesNotTriggerCompensation() {
        String vnfId = "vnf-reply-" + System.currentTimeMillis();
        UUID sagaId = sagaOrchestrator.startInstantiateSaga(vnfId, Map.of("cpu", 2));

        sagaOrchestrator.handleReply(sagaId, 1, true, Map.of("reservationId", "res-1"));

        var timeouts = sagaTimeoutRepository.findBySagaIdAndStepAndProcessedFalse(sagaId.toString(), 1);
        assertThat(timeouts).isEmpty();

        SagaInstance saga = sagaInstanceRepository.findBySagaId(sagaId.toString()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(saga.getCurrentStep()).isEqualTo(2);

        timeoutScheduler.processDueTimeouts();

        saga = sagaInstanceRepository.findBySagaId(sagaId.toString()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
    }
}
