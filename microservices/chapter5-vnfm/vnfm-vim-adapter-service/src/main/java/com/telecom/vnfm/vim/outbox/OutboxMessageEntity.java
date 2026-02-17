package com.telecom.vnfm.vim.outbox;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
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
}
