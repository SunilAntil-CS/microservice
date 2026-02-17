package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when the VIM has allocated resources for a VNF (to be used later in the flow).
 */
public class VimResourcesAllocatedEvent extends AbstractDomainEvent {

    private final String vnfId;
    private final String vimResourceId;
    private final String ipAddress;

    public VimResourcesAllocatedEvent(String vnfId, String vimResourceId, String ipAddress, int version, Instant timestamp) {
        super(vnfId, version, timestamp);
        this.vnfId = vnfId;
        this.vimResourceId = vimResourceId;
        this.ipAddress = ipAddress;
    }

    public VimResourcesAllocatedEvent(String vnfId, String vimResourceId, String ipAddress) {
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
