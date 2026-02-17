package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when VNF instantiation has been started (resources requested).
 */
public class VnfInstantiationStarted extends AbstractDomainEvent {

    private final String vnfId;
    private final String resources;

    public VnfInstantiationStarted(String vnfId, String resources, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.resources = resources;
    }

    /** For rebuilding from event store (preserves eventId). */
    public VnfInstantiationStarted(String eventId, String vnfId, String resources, int version, Instant timestamp) {
        super(eventId, vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.resources = resources;
    }

    public VnfInstantiationStarted(String vnfId, String resources) {
        this(vnfId, resources, 1, Instant.now());
    }

    public String getVnfId() {
        return vnfId;
    }

    public String getResources() {
        return resources;
    }
}
