package com.vnfm.lcm.infrastructure.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.event.*;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Serializes and deserializes domain events to/from JSON for storage.
 */
@Component
public class DomainEventSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String serialize(DomainEvent event) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "eventType", event.getClass().getSimpleName(),
                    "eventId", event.getEventId(),
                    "aggregateId", event.getAggregateId(),
                    "version", event.getVersion(),
                    "timestamp", event.getTimestamp().toString(),
                    "payload", toPayloadMap(event)
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize event", e);
        }
    }

    @SuppressWarnings("unchecked")
    public DomainEvent deserialize(String payload) {
        return deserialize(payload, null);
    }

    @SuppressWarnings("unchecked")
    public DomainEvent deserialize(String payload, String aggregateType) {
        try {
            Map<String, Object> root = MAPPER.readValue(payload, Map.class);
            String eventType = (String) root.get("eventType");
            Map<String, Object> p = (Map<String, Object>) root.getOrDefault("payload", root);
            String eventId = (String) root.get("eventId");
            String aggregateId = (String) root.get("aggregateId");
            int version = ((Number) root.get("version")).intValue();
            Instant timestamp = Instant.parse((String) root.get("timestamp"));

            return switch (eventType) {
                case "VnfInstanceCreated" -> new VnfInstanceCreated(
                        eventId, aggregateId,
                        (String) p.get("vnfInstanceName"), (String) p.get("vnfInstanceDescription"),
                        version, timestamp);
                case "VnfInstantiationStarted" -> new VnfInstantiationStarted(
                        eventId, aggregateId,
                        (String) p.get("vnfId"), (String) p.get("resources"),
                        version, timestamp);
                case "VnfInstantiated" -> new VnfInstantiated(
                        eventId, aggregateId,
                        (String) p.get("vimResourceId"), (String) p.get("ipAddress"),
                        version, timestamp);
                case "VnfInstantiationFailed" -> new VnfInstantiationFailed(
                        eventId, aggregateId, (String) p.get("reason"), version, timestamp);
                case "VnfTerminationStarted" -> new VnfTerminationStarted(
                        eventId, aggregateId, version, timestamp);
                case "VnfTerminated" -> new VnfTerminated(
                        eventId, aggregateId, version, timestamp);
                case "VnfTerminationFailed" -> new VnfTerminationFailed(
                        eventId, aggregateId, (String) p.get("reason"), version, timestamp);
                case "OpOccCreated" -> new OpOccCreated(
                        eventId, aggregateId, (String) p.get("vnfId"), (String) p.get("operationType"),
                        version, timestamp);
                case "OpOccUpdated" -> new OpOccUpdated(
                        eventId, aggregateId, (String) p.get("state"), version, timestamp);
                case "OpOccCompleted" -> new OpOccCompleted(eventId, aggregateId, version, timestamp);
                case "OpOccFailed" -> new OpOccFailed(
                        eventId, aggregateId, (String) p.get("errorMessage"), version, timestamp);
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize event", e);
        }
    }

    private Map<String, Object> toPayloadMap(DomainEvent event) {
        if (event instanceof VnfInstanceCreated e) {
            return Map.of("vnfInstanceName", nullToEmpty(e.getVnfInstanceName()), "vnfInstanceDescription", nullToEmpty(e.getVnfInstanceDescription()));
        }
        if (event instanceof VnfInstantiationStarted e) {
            return Map.of("vnfId", e.getVnfId(), "resources", e.getResources() != null ? e.getResources() : "");
        }
        if (event instanceof VnfInstantiated e) {
            return Map.of("vnfId", e.getVnfId(), "vimResourceId", nullToEmpty(e.getVimResourceId()), "ipAddress", nullToEmpty(e.getIpAddress()));
        }
        if (event instanceof VnfInstantiationFailed e) {
            return Map.of("vnfId", e.getVnfId(), "reason", nullToEmpty(e.getReason()));
        }
        if (event instanceof VnfTerminationStarted e) {
            return Map.of("vnfId", e.getVnfId());
        }
        if (event instanceof VnfTerminated e) {
            return Map.of("vnfId", e.getVnfId());
        }
        if (event instanceof VnfTerminationFailed e) {
            return Map.of("vnfId", e.getVnfId(), "reason", nullToEmpty(e.getReason()));
        }
        if (event instanceof OpOccCreated e) {
            return Map.of("vnfId", nullToEmpty(e.getVnfId()), "operationType", nullToEmpty(e.getOperationType()));
        }
        if (event instanceof OpOccUpdated e) {
            return Map.of("state", nullToEmpty(e.getState()));
        }
        if (event instanceof OpOccCompleted e) {
            return Map.of();
        }
        if (event instanceof OpOccFailed e) {
            return Map.of("errorMessage", nullToEmpty(e.getErrorMessage()));
        }
        throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
