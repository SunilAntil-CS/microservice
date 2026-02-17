package com.telecom.fms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Business entity: one trouble ticket per unique alarm event (after idempotency check).
 */
@Entity
@Table(name = "trouble_ticket")
public class TroubleTicket {

    @Id
    private String id;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "link_id")
    private String linkId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at")
    private Instant createdAt;

    public TroubleTicket() {}

    public TroubleTicket(String id, String nodeId, String linkId, String type) {
        this.id = id;
        this.nodeId = nodeId;
        this.linkId = linkId;
        this.type = type;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getLinkId() { return linkId; }
    public void setLinkId(String linkId) { this.linkId = linkId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
