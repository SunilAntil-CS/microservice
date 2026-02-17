package com.telecom.vnfm.lcm.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates and configures the {@link org.springframework.kafka.core.KafkaTemplate} used by this service.
 * ---------------------------------------------------------------------------
 * This class is the only place that defines the KafkaTemplate bean. It does two things:
 * (1) Defines producer settings via ProducerFactory (bootstrap, serializers, acks, retries, idempotence).
 * (2) Creates the KafkaTemplate bean that uses that factory; Spring then injects it wherever needed (e.g. OutboxRelay).
 *
 * <h3>Flow diagram</h3>
 * <pre>
 * KafkaProducerConfig
 *        |
 *        +-- producerFactory()  --> ProducerFactory (bootstrap, serializers, acks, retries, idempotence)
 *        |
 *        +-- kafkaTemplate()    --> new KafkaTemplate(producerFactory())
 *                     |
 *                     v
 *             Spring registers this as the KafkaTemplate&lt;String, String&gt; bean
 *                     |
 *                     v
 *             OutboxRelay (and others) inject it and call .send(...)
 * </pre>
 *
 * <p>Producer settings:</p>
 * <ul>
 *   <li>acks=all: leader and replicas must acknowledge (durability).</li>
 *   <li>idempotence: prevents duplicate sends on retry (exactly-once semantics for producer).</li>
 *   <li>retries: transient failures (e.g. leader election) are retried.</li>
 *   <li>key/value serializers: we send payload as JSON string; key = outbox id for partitioning.</li>
 * </ul>
 *
 * <p>Note: {@link KafkaConsumerConfig} does not create or use KafkaTemplate; it configures consumers only.</p>
 */
@Configuration
public class KafkaProducerConfig {

    /** Comma-separated list of Kafka brokers (e.g. localhost:9092). Used to connect the producer to the cluster. */
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Builds the producer factory with connection and reliability settings.
     * Used by {@link #kafkaTemplate()} to create the underlying Kafka producer(s).
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        // Broker list; producer discovers rest of cluster from these bootstrap servers
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Serialize record key as String (we use outbox message id as key for partitioning)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Serialize record value as String (JSON payload from outbox)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Leader and all in-sync replicas must ack; ensures durability before we consider send successful
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Number of retries on transient failures (e.g. leader election, network blip)
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Idempotent producer: prevents duplicate writes on retry (exactly-once semantics at producer level)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * The KafkaTemplate bean injected by OutboxRelay (and any other producer). Wraps the producer factory.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
