package com.vnfm.lcm.infrastructure.readside;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Read-side table of known VNF IDs for listing (GET /api/vnfs).
 * Populated when a new VNF is created (POST /api/vnfs).
 */
@Entity
@Table(name = "vnf_index", indexes = {
        @Index(name = "idx_vnf_index_vnf_id", columnList = "vnf_id", unique = true)
})
public class VnfIndex {

    @Id
    @Column(name = "vnf_id", length = 36, nullable = false)
    private String vnfId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("unused")
    public VnfIndex() {
    }

    public VnfIndex(String vnfId) {
        this.vnfId = vnfId;
        this.createdAt = Instant.now();
    }

    public String getVnfId() {
        return vnfId;
    }

    public void setVnfId(String vnfId) {
        this.vnfId = vnfId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
