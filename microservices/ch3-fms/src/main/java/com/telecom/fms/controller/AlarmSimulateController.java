package com.telecom.fms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.fms.model.LinkDownEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Optional: simulate a device sending a "Link Down" event. Publishes to Kafka so the
 * idempotent consumer processes it. Use the same messageId twice to verify only one
 * ticket is created (idempotency). For production, events come from real devices/alarms.
 */
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmSimulateController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public AlarmSimulateController(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${fms.kafka.topic:alarm-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    /**
     * POST with optional messageId in body to test idempotency. If messageId omitted, we generate one.
     * Body: { "nodeId": "node-1", "linkId": "link-1", "severity": "MAJOR", "messageId": "optional-uuid" }
     */
    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SimulateResponse simulate(@RequestBody SimulateRequest request) {
        String messageId = request.messageId != null && !request.messageId.isBlank()
                ? request.messageId
                : UUID.randomUUID().toString();
        LinkDownEvent event = new LinkDownEvent(
                request.nodeId != null ? request.nodeId : "node-unknown",
                request.linkId != null ? request.linkId : "",
                request.severity != null ? request.severity : "MAJOR"
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise event", e);
        }
        kafkaTemplate.send(topic, messageId, payload);
        return new SimulateResponse(messageId, "Event sent to " + topic);
    }

    public record SimulateRequest(String nodeId, String linkId, String severity, String messageId) {}
    public record SimulateResponse(String messageId, String status) {}
}
