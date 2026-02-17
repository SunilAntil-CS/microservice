package com.vnfm.lcm.infrastructure.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OutboxForwarder using mocks.
 * STUDY NOTE: We mock OutboxRepository and MessagePublisher so we test only the forwarder
 * logic: load due messages, publish, update status on success or retry fields on failure.
 */
@ExtendWith(MockitoExtension.class)
class OutboxForwarderTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private OutboxForwarder outboxForwarder;

    private OutboxMessage pendingMessage;

    @BeforeEach
    void setUp() {
        pendingMessage = new OutboxMessage("msg-1", "vim.manager", "InstantiateVnfCommand", "{}");
        pendingMessage.setId(1L);
        pendingMessage.setNextRetryAt(Instant.now().minusSeconds(1));
    }

    @Test
    void forward_whenNoDueMessages_doesNotCallPublisher() {
        when(outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OutboxStatus.PENDING, any(Instant.class)))
                .thenReturn(List.of());

        outboxForwarder.forward();

        verify(messagePublisher, never()).publish(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void forward_whenPublishSucceeds_setsStatusToSentAndSaves() {
        when(outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OutboxStatus.PENDING, any(Instant.class)))
                .thenReturn(List.of(pendingMessage));

        outboxForwarder.forward();

        verify(messagePublisher).publish(pendingMessage);
        assertThat(pendingMessage.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(pendingMessage.getLastError()).isNull();

        ArgumentCaptor<OutboxMessage> saved = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void forward_whenPublishFails_incrementsRetryCountAndSetsNextRetryAtAndLastError() {
        when(outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OutboxStatus.PENDING, any(Instant.class)))
                .thenReturn(List.of(pendingMessage));
        doThrow(new PublishException("Broker unavailable"))
                .when(messagePublisher).publish(pendingMessage);

        outboxForwarder.forward();

        verify(messagePublisher).publish(pendingMessage);
        assertThat(pendingMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingMessage.getRetryCount()).isEqualTo(1);
        assertThat(pendingMessage.getLastError()).contains("Broker unavailable");
        assertThat(pendingMessage.getNextRetryAt()).isAfter(Instant.now());

        verify(outboxRepository).save(pendingMessage);
    }

    @Test
    void forward_whenPublishFailsTwice_exponentialBackoffIncreasesDelay() {
        pendingMessage.setRetryCount(1);
        when(outboxRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                OutboxStatus.PENDING, any(Instant.class)))
                .thenReturn(List.of(pendingMessage));
        doThrow(new PublishException("Broker unavailable"))
                .when(messagePublisher).publish(pendingMessage);

        outboxForwarder.forward();

        // retryCount was 1, after failure becomes 2; delay = 2 * 2^2 = 8 seconds
        assertThat(pendingMessage.getRetryCount()).isEqualTo(2);
        Instant nextRetry = pendingMessage.getNextRetryAt();
        assertThat(nextRetry).isAfter(Instant.now().plusSeconds(7));
    }
}
