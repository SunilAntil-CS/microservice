package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when VNF termination has been started.
 */
public class VnfTerminationStarted extends AbstractDomainEvent {

    private final String vnfId;

    public VnfTerminationStarted(String vnfId, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
    }

    /** For rebuilding from event store (preserves eventId). */
    public VnfTerminationStarted(String eventId, String vnfId, int version, Instant timestamp) {
        super(eventId, vnfId, version, timestamp);
        this.vnfId = vnfId;
    }

    public VnfTerminationStarted(String vnfId) {
        this(vnfId, 1, Instant.now());
    }

    public String getVnfId() {
        return vnfId;
    }
}
