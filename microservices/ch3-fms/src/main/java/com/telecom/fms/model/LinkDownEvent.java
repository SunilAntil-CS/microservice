package com.telecom.fms.model;

/**
 * Event payload: microwave link down (from device / alarm system).
 * Consumed from Kafka; we use it to create a single trouble ticket per unique message_id.
 * Jackson deserialises JSON to this record (canonical constructor).
 */
public record LinkDownEvent(String nodeId, String linkId, String severity) {
    public LinkDownEvent {
        if (nodeId == null) nodeId = "";
        if (linkId == null) linkId = "";
        if (severity == null) severity = "MAJOR";
    }
}
