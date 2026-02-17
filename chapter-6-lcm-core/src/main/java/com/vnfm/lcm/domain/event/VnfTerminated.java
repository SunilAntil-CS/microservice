package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when the VNF has been successfully terminated.
 */
public class VnfTerminated extends AbstractDomainEvent {

    private final String vnfId;

    public VnfTerminated(String vnfId, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
    }

    /** For rebuilding from event store (preserves eventId). */
    public VnfTerminated(String eventId, String vnfId, int version, Instant timestamp) {
        super(eventId, vnfId, version, timestamp);
        this.vnfId = vnfId;
    }

    public VnfTerminated(String vnfId) {
        this(vnfId, 1, Instant.now());
    }

    public String getVnfId() {
        return vnfId;
    }
}
