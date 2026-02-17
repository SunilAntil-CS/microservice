package com.vnfm.lcm.domain.model;

import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.command.InstantiateVnfCommand;
import com.vnfm.lcm.domain.event.VnfInstanceCreated;
import com.vnfm.lcm.domain.event.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * VNF aggregate: maintains current state of a VNF and applies domain events.
 * Commands are processed to produce new events; events are applied to update state and version.
 */
public class VnfAggregate {

    private UUID vnfId;
    private VnfState state;
    private String vimResourceId;
    private String ipAddress;
    private int version;

    /** No-args constructor for rebuilding from event stream (used by from(List)). */
    private VnfAggregate() {
        this.state = VnfState.INITIAL;
        this.version = 0;
    }

    /**
     * Factory for a new aggregate (no events applied yet).
     */
    public static VnfAggregate create(UUID vnfId) {
        VnfAggregate a = new VnfAggregate();
        a.vnfId = vnfId;
        return a;
    }

    /**
     * Rebuild aggregate by applying each event in order. Uses no-args constructor then apply.
     */
    public static VnfAggregate from(List<DomainEvent> events) {
        VnfAggregate a = new VnfAggregate();
        for (DomainEvent e : events) {
            a.applyEvent(e);
        }
        return a;
    }

    private void applyEvent(DomainEvent e) {
        if (e instanceof VnfInstanceCreated evt) {
            apply(evt);
        } else if (e instanceof VnfInstantiationStarted evt) {
            apply(evt);
        } else if (e instanceof VnfInstantiated evt) {
            apply(evt);
        } else if (e instanceof VnfInstantiationFailed evt) {
            apply(evt);
        } else if (e instanceof VnfTerminationStarted evt) {
            apply(evt);
        } else if (e instanceof VnfTerminated evt) {
            apply(evt);
        } else if (e instanceof VnfTerminationFailed evt) {
            apply(evt);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + e.getClass().getName());
        }
    }

    public void apply(VnfInstanceCreated event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.INITIAL;
        this.version = event.getVersion();
    }

    /**
     * Process instantiate command: validate and return new event(s). Only valid when state is INITIAL.
     */
    public List<DomainEvent> process(InstantiateVnfCommand command) {
        if (vnfId != null && !vnfId.toString().equals(command.vnfId())) {
            throw new IllegalArgumentException("Command vnfId does not match aggregate");
        }
        if (state != VnfState.INITIAL) {
            throw new IllegalStateException("Cannot instantiate: current state is " + state);
        }
        String resources = "vnfType=" + command.vnfType() + ", cpuCores=" + command.cpuCores() + ", memoryGb=" + command.memoryGb();
        int nextVersion = version + 1;
        VnfInstantiationStarted event = new VnfInstantiationStarted(
                command.vnfId(),
                resources,
                nextVersion,
                Instant.now()
        );
        return List.of(event);
    }

    /**
     * Process VIM resources allocated event (to be used later). For now throws.
     */
    public List<DomainEvent> process(VimResourcesAllocatedEvent event) {
        throw new UnsupportedOperationException("VimResourcesAllocatedEvent processing not yet implemented");
    }

    public void apply(VnfInstantiationStarted event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.INSTANTIATING;
        this.version = event.getVersion();
    }

    public void apply(VnfInstantiated event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.ACTIVE;
        this.vimResourceId = event.getVimResourceId();
        this.ipAddress = event.getIpAddress();
        this.version = event.getVersion();
    }

    public void apply(VnfInstantiationFailed event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.FAILED;
        this.version = event.getVersion();
    }

    public void apply(VnfTerminationStarted event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.TERMINATING;
        this.version = event.getVersion();
    }

    public void apply(VnfTerminated event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.TERMINATED;
        this.version = event.getVersion();
    }

    public void apply(VnfTerminationFailed event) {
        ensureVersion(event.getVersion());
        this.vnfId = UUID.fromString(event.getAggregateId());
        this.state = VnfState.FAILED;
        this.version = event.getVersion();
    }

    private void ensureVersion(int eventVersion) {
        if (eventVersion != version + 1) {
            throw new IllegalStateException("Event version " + eventVersion + " does not follow current version " + version);
        }
    }

    // --- Getters ---

    public UUID getVnfId() {
        return vnfId;
    }

    public VnfState getState() {
        return state;
    }

    public String getVimResourceId() {
        return vimResourceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getVersion() {
        return version;
    }
}
