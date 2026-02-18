package com.vnfm.lcm.infrastructure.debezium;

import io.debezium.engine.ChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Verifies that when the DebeziumListener receives a change event for the {@code events}
 * table (as it would from the embedded Debezium engine), it publishes to the vnf.events
 * Kafka topic with the correct key and value.
 */
@ExtendWith(MockitoExtension.class)
class DebeziumListenerTest {

    @Mock
    private EventPublisher eventPublisher;

    private DebeziumProperties properties;
    private DebeziumListener listener;

    @BeforeEach
    void setUp() {
        properties = new DebeziumProperties();
        properties.setEventsTopic("vnf.events");
        listener = new DebeziumListener(properties, eventPublisher);
    }

    @Test
    void whenEventsTableChangeEvent_receivesEvent_publishesToVnfEventsTopic() {
        String eventId = "evt-12345";
        String cdcJson = "{\"source\":{\"table\":\"events\",\"schema\":\"public\",\"db\":\"vnfm_db\"},\"after\":{\"id\":1,\"event_id\":\"" + eventId + "\",\"aggregate_id\":\"agg-1\",\"aggregate_type\":\"VNF\",\"version\":1,\"event_type\":\"VnfInstantiationStarted\",\"payload\":\"{}\",\"event_timestamp\":\"2025-02-18T10:00:00Z\"},\"op\":\"c\"}";

        ChangeEvent<String, String> changeEvent = new ChangeEvent<>() {
            @Override
            public String key() {
                return null;
            }

            @Override
            public String value() {
                return cdcJson;
            }

            @Override
            public String destination() {
                return "vnf.events";
            }

            @Override
            public Integer partition() {
                return null;
            }
        };

        listener.handleChangeEvent(changeEvent);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("vnf.events");
        assertThat(keyCaptor.getValue()).isEqualTo(eventId);
        assertThat(valueCaptor.getValue()).contains("\"event_id\":\"" + eventId + "\"");
        assertThat(valueCaptor.getValue()).contains("events");
    }

    @Test
    void whenOutboxTableChangeEvent_publishesToDestinationAsTopic() {
        String messageId = "msg-67890";
        String destination = "vim.commands";
        String cdcJson = "{\"source\":{\"table\":\"outbox\",\"schema\":\"public\"},\"after\":{\"id\":1,\"message_id\":\"" + messageId + "\",\"destination\":\"" + destination + "\",\"message_type\":\"ReserveResources\",\"payload\":\"{}\",\"status\":\"PENDING\"},\"op\":\"c\"}";

        ChangeEvent<String, String> eventWithValue = new ChangeEvent<>() {
            @Override
            public String key() {
                return null;
            }

            @Override
            public String value() {
                return cdcJson;
            }

            @Override
            public String destination() {
                return "vim.commands";
            }

            @Override
            public Integer partition() {
                return null;
            }
        };

        listener.handleChangeEvent(eventWithValue);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("vim.commands");
        assertThat(keyCaptor.getValue()).isEqualTo(messageId);
    }
}
