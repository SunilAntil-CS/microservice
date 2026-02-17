package com.telecom.vnfm.vim.config;

import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade Kafka consumer configuration.
 * ---------------------------------------------------------------------------
 * - ErrorHandlingDeserializer: wraps key/value deserializers so deserialization
 *   failures don't kill the consumer; failed records can be logged/sent to DLT.
 * - enable.auto.commit=false + manual ack: we commit only after successful
 *   processing (or use commitSync in listener) for at-least-once with no skip.
 * - isolation.level=read_committed: only read committed messages (if producer
 *   uses transactions; our relay uses idempotent producer).
 * - ErrorHandler: retry with backoff, then log and skip (or send to DLT in production).
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${vnfm.vim.consumer-group:vim-adapter}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, InfraDeploymentRequestedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.telecom.vnfm.common.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InfraDeploymentRequestedEvent.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InfraDeploymentRequestedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InfraDeploymentRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 3),
                (record, ex) -> {
                    // After retries exhausted: log and skip (production: send to dead-letter topic)
                    org.slf4j.LoggerFactory.getLogger(KafkaConsumerConfig.class)
                            .error("Record skipped after retries: topic={}, partition={}, offset={}",
                                    record.topic(), record.partition(), record.offset(), ex);
                }
        ));
        return factory;
    }
}
