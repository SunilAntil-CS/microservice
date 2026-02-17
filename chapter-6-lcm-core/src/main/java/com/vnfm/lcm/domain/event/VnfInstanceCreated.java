package com.vnfm.lcm.domain.event;

import java.time.Instant;

/**
 * Emitted when a VNF instance is created (POST /vnf_instances).
 * Puts the aggregate in NOT_INSTANTIATED (INITIAL) state.
 */
public class VnfInstanceCreated extends AbstractDomainEvent {

    private final String vnfInstanceName;
    private final String vnfInstanceDescription;

    public VnfInstanceCreated(String aggregateId, String vnfInstanceName, String vnfInstanceDescription,
                              int version, Instant timestamp) {
        super(aggregateId, version, timestamp);
        this.vnfInstanceName = vnfInstanceName != null ? vnfInstanceName : "";
        this.vnfInstanceDescription = vnfInstanceDescription != null ? vnfInstanceDescription : "";
    }

    public VnfInstanceCreated(String eventId, String aggregateId, String vnfInstanceName,
                              String vnfInstanceDescription, int version, Instant timestamp) {
        super(eventId, aggregateId, version, timestamp);
        this.vnfInstanceName = vnfInstanceName != null ? vnfInstanceName : "";
        this.vnfInstanceDescription = vnfInstanceDescription != null ? vnfInstanceDescription : "";
    }

    public String getVnfInstanceName() {
        return vnfInstanceName;
    }

    public String getVnfInstanceDescription() {
        return vnfInstanceDescription;
    }
}
