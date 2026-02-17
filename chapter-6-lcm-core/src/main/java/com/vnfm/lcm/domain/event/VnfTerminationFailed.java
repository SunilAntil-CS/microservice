package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when VNF termination has failed.
 */
public class VnfTerminationFailed extends AbstractDomainEvent {

    private final String vnfId;
    private final String reason;

    public VnfTerminationFailed(String vnfId, String reason, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.reason = reason;
    }

    /** For rebuilding from event store (preserves eventId). */
    public VnfTerminationFailed(String eventId, String vnfId, String reason, int version, Instant timestamp) {
        super(eventId, vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.reason = reason;
    }

    public VnfTerminationFailed(String vnfId, String reason) {
        this(vnfId, reason, 1, Instant.now());
    }

    public String getVnfId() {
        return vnfId;
    }

    public String getReason() {
        return reason;
    }
}
