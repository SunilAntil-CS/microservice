package com.telecom.vnfm.lcm.config;

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
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates and configures the Kafka CONSUMER side for all VIM → LCM events (ACK, Progress, Reply, Failed).
 * ---------------------------------------------------------------------------
 * This class does NOT create or use {@link org.springframework.kafka.core.KafkaTemplate}. It does two things:
 * (1) Defines consumer settings via ConsumerFactory (bootstrap, groupId, deserializers, manual commit).
 * (2) Creates the kafkaListenerContainerFactory bean that @KafkaListener methods use; Spring wires it to LcmEventHandler.
 *
 * <h3>Flow diagram</h3>
 * <pre>
 * KafkaConsumerConfig
 *        |
 *        +-- consumerFactory()              --> ConsumerFactory (bootstrap, groupId, String deserializers,
 *        |                                    autoOffsetReset=earliest, enableAutoCommit=false)
 *        |
 *        +-- kafkaListenerContainerFactory() --> ConcurrentKafkaListenerContainerFactory
 *                     |                        (uses consumerFactory, MANUAL_IMMEDIATE ack, error handler)
 *                     v
 *             Spring registers this as "kafkaListenerContainerFactory" bean
 *                     |
 *                     v
 *             LcmEventHandler @KafkaListener(..., containerFactory = "kafkaListenerContainerFactory")
 *             consumes from infra-deployment-accepted, -progress, infra-deployed-reply, infra-deployment-failed
 * </pre>
 *
 * <p>Consumer settings:</p>
 * <ul>
 *   <li>Value type is String; listeners deserialize JSON to the appropriate event type in code.</li>
 *   <li>Manual ack: we acknowledge only after successful processing and recording in ProcessedMessageRepository (idempotent shield).</li>
 *   <li>Error handler: FixedBackOff 3 retries, then log and skip record.</li>
 * </ul>
 *
 * <p>Note: Sending to Kafka is done via KafkaTemplate, which is created in {@link KafkaProducerConfig} and used by OutboxRelay.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${vnfm.lcm.consumer.group-id:lcm-service-group}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 3),
                (record, ex) -> org.slf4j.LoggerFactory.getLogger(KafkaConsumerConfig.class)
                        .error("Record skipped after retries: topic={}, partition={}, offset={}",
                                record.topic(), record.partition(), record.offset(), ex)
        ));
        return factory;
    }
}
