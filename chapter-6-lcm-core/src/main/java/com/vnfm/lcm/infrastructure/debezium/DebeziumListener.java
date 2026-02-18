package com.vnfm.lcm.infrastructure.debezium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts an embedded Debezium engine for PostgreSQL that captures changes on
 * {@code events} and {@code outbox} tables and publishes each change to Kafka.
 * <ul>
 *   <li>Events table → topic {@code vnf.events}</li>
 *   <li>Outbox table → topic from the row's {@code destination} column (e.g. vim.commands)</li>
 * </ul>
 * The engine runs in a separate thread and is managed by Spring lifecycle
 * ({@link PostConstruct} start, {@link PreDestroy} stop).
 *
 * Active only when {@code lcm.publisher.mode=debezium-cdc}. Use
 * {@code lcm.publisher.mode=outbox-forwarder} to use the scheduled OutboxForwarder instead.
 */
@Component
@ConditionalOnProperty(name = "lcm.publisher.mode", havingValue = "debezium-cdc")
public class DebeziumListener {

    private static final Logger log = LoggerFactory.getLogger(DebeziumListener.class);

    private static final String TABLE_EVENTS = "events";
    private static final String TABLE_OUTBOX = "outbox";
    private static final String COLUMN_DESTINATION = "destination";
    private static final String COLUMN_EVENT_ID = "event_id";
    private static final String COLUMN_MESSAGE_ID = "message_id";

    private final DebeziumProperties properties;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private ExecutorService executor;

    public DebeziumListener(DebeziumProperties properties, EventPublisher eventPublisher) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            return;
        }
        DebeziumProperties.Database db = properties.getDatabase();
        Properties config = new Properties();
        config.setProperty("name", properties.getConnectorName());
        config.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        config.setProperty("database.hostname", db.getHostname());
        config.setProperty("database.port", String.valueOf(db.getPort()));
        config.setProperty("database.user", db.getUsername());
        config.setProperty("database.password", db.getPassword());
        config.setProperty("database.dbname", db.getDbname());
        config.setProperty("table.include.list", properties.getTableIncludeList());
        config.setProperty("plugin.name", "pgoutput");
        config.setProperty("slot.name", "lcm_cdc_slot");
        config.setProperty("publication.name", "lcm_cdc_pub");
        config.setProperty("snapshot.mode", "initial");

        engine = DebeziumEngine.create(Json.class)
                .using(config)
                .notifying(this::handleChangeEvent)
                .build();

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "debezium-cdc-engine");
            t.setDaemon(false);
            return t;
        });
        executor.submit(engine);
        log.info("Debezium CDC engine started for tables: {}", properties.getTableIncludeList());
    }

    @PreDestroy
    public void stop() {
        if (engine != null) {
            try {
                engine.close();
                log.info("Debezium CDC engine closed");
            } catch (Exception e) {
                log.warn("Error closing Debezium engine: {}", e.getMessage());
            }
            engine = null;
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            executor = null;
        }
    }

    /** Package-private for testing: process a single CDC event. */
    void handleChangeEvent(ChangeEvent<String, String> event) {
        if (event == null || event.value() == null) {
            return;
        }
        try {
            String value = event.value();
            // The engine is configured with Json.class so value is already JSON string.
            JsonNode root = objectMapper.readTree(value);
            JsonNode source = root.path("source");
            if (source.isMissingNode()) {
                log.warn("CDC event missing 'source': {}", value.length() > 200 ? value.substring(0, 200) + "..." : value);
                return;
            }
            String table = source.path("table").asText("");
            JsonNode after = root.path("after");
            if (after.isMissingNode()) {
                log.debug("CDC event has no 'after' (tombstone/delete): table={}", table);
                return;
            }

            String topic;
            String key;
            if (TABLE_EVENTS.equals(table)) {
                topic = properties.getEventsTopic();
                key = after.has(COLUMN_EVENT_ID) ? after.get(COLUMN_EVENT_ID).asText() : after.path("id").asText();
            } else if (TABLE_OUTBOX.equals(table)) {
                String destination = after.path(COLUMN_DESTINATION).asText("");
                topic = destination.isEmpty() ? "outbox.unknown" : destination;
                key = after.has(COLUMN_MESSAGE_ID) ? after.get(COLUMN_MESSAGE_ID).asText() : after.path("id").asText();
            } else {
                log.debug("Ignoring CDC event for unconfigured table: {}", table);
                return;
            }

            eventPublisher.publish(topic, key, value);
        } catch (Exception e) {
            log.error("Error handling CDC event: {}", e.getMessage(), e);
        }
    }
}
