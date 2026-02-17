package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when the VNF has been successfully instantiated and is running.
 */
public class VnfInstantiated extends AbstractDomainEvent {

    private final String vnfId;
    private final String vimResourceId;
    private final String ipAddress;

    public VnfInstantiated(String vnfId, String vimResourceId, String ipAddress, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.vimResourceId = vimResourceId;
        this.ipAddress = ipAddress;
    }

    /** For rebuilding from event store (preserves eventId). */
    public VnfInstantiated(String eventId, String vnfId, String vimResourceId, String ipAddress, int version, Instant timestamp) {
        super(eventId, vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.vimResourceId = vimResourceId;
        this.ipAddress = ipAddress;
    }

    public VnfInstantiated(String vnfId, String vimResourceId, String ipAddress) {
        this(vnfId, vimResourceId, ipAddress, 1, Instant.now());
    }

    public String getVnfId() {
        return vnfId;
    }

    public String getVimResourceId() {
        return vimResourceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
