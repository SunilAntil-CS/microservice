package com.telecom.vnfm.lcm.outbox;

import javax.persistence.*;
import java.util.UUID;

/**
 * Outbox table entity (same concept as Eventuate Tram's "message" table).
 * ---------------------------------------------------------------------------
 * Written in the same @Transactional method as VnfInstance.save(). The relay
 * (or CDC) reads unpublished rows and sends payload to Kafka. No Kafka call
 * inside the business transaction.
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(length = 2000)
    private String headers;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(nullable = false)
    private int published = 0;

    protected OutboxMessageEntity() {
    }

    public static OutboxMessageEntity create(String destination, String payload) {
        OutboxMessageEntity m = new OutboxMessageEntity();
        m.id = UUID.randomUUID().toString();
        m.destination = destination;
        m.headers = "{}";
        m.payload = payload;
        m.published = 0;
        return m;
    }

    public String getId() { return id; }
    public String getDestination() { return destination; }
    public String getPayload() { return payload; }
    public int getPublished() { return published; }
    public void setPublished(int published) { this.published = published; }
}
