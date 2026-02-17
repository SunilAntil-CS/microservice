package com.vnfm.lcm.domain.model;

import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.command.InstantiateVnfCommand;
import com.vnfm.lcm.domain.event.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnfAggregateTest {

    private static final UUID VNF_ID = UUID.randomUUID();
    private static final String VNF_ID_STR = VNF_ID.toString();

    @Test
    void create_returnsAggregateInInitialState() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);

        assertThat(agg.getVnfId()).isEqualTo(VNF_ID);
        assertThat(agg.getState()).isEqualTo(VnfState.INITIAL);
        assertThat(agg.getVersion()).isEqualTo(0);
        assertThat(agg.getVimResourceId()).isNull();
        assertThat(agg.getIpAddress()).isNull();
    }

    @Test
    void process_instantiateCommand_returnsVnfInstantiationStartedEvent() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);
        InstantiateVnfCommand cmd = new InstantiateVnfCommand(VNF_ID_STR, "firewall", 2, 4, "req-1");

        List<DomainEvent> events = agg.process(cmd);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(VnfInstantiationStarted.class);
        VnfInstantiationStarted started = (VnfInstantiationStarted) events.get(0);
        assertThat(started.getVnfId()).isEqualTo(VNF_ID_STR);
        assertThat(started.getResources()).contains("firewall", "cpuCores=2", "memoryGb=4");
        assertThat(started.getVersion()).isEqualTo(1);
    }

    @Test
    void process_instantiateCommand_whenNotInitial_throws() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);
        agg.apply(new VnfInstantiationStarted(VNF_ID_STR, "res", 1, Instant.now()));
        InstantiateVnfCommand cmd = new InstantiateVnfCommand(VNF_ID_STR, "firewall", 2, 4, "req-1");

        assertThatThrownBy(() -> agg.process(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTANTIATING");
    }

    @Test
    void apply_events_changesStateCorrectly() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);

        agg.apply(new VnfInstantiationStarted(VNF_ID_STR, "res", 1, Instant.now()));
        assertThat(agg.getState()).isEqualTo(VnfState.INSTANTIATING);
        assertThat(agg.getVersion()).isEqualTo(1);

        agg.apply(new VnfInstantiated(VNF_ID_STR, "vim-123", "10.0.0.1", 2, Instant.now()));
        assertThat(agg.getState()).isEqualTo(VnfState.ACTIVE);
        assertThat(agg.getVersion()).isEqualTo(2);
        assertThat(agg.getVimResourceId()).isEqualTo("vim-123");
        assertThat(agg.getIpAddress()).isEqualTo("10.0.0.1");

        agg.apply(new VnfTerminationStarted(VNF_ID_STR, 3, Instant.now()));
        assertThat(agg.getState()).isEqualTo(VnfState.TERMINATING);
        assertThat(agg.getVersion()).isEqualTo(3);

        agg.apply(new VnfTerminated(VNF_ID_STR, 4, Instant.now()));
        assertThat(agg.getState()).isEqualTo(VnfState.TERMINATED);
        assertThat(agg.getVersion()).isEqualTo(4);
    }

    @Test
    void apply_instantiationFailed_setsFailedState() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);
        agg.apply(new VnfInstantiationStarted(VNF_ID_STR, "res", 1, Instant.now()));
        agg.apply(new VnfInstantiationFailed(VNF_ID_STR, "VIM timeout", 2, Instant.now()));

        assertThat(agg.getState()).isEqualTo(VnfState.FAILED);
        assertThat(agg.getVersion()).isEqualTo(2);
    }

    @Test
    void apply_terminationFailed_setsFailedState() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);
        agg.apply(new VnfInstantiationStarted(VNF_ID_STR, "res", 1, Instant.now()));
        agg.apply(new VnfInstantiated(VNF_ID_STR, "vim-1", "10.0.0.1", 2, Instant.now()));
        agg.apply(new VnfTerminationStarted(VNF_ID_STR, 3, Instant.now()));
        agg.apply(new VnfTerminationFailed(VNF_ID_STR, "VIM error", 4, Instant.now()));

        assertThat(agg.getState()).isEqualTo(VnfState.FAILED);
        assertThat(agg.getVersion()).isEqualTo(4);
    }

    @Test
    void from_rebuildingFromEvents_yieldsCorrectState() {
        Instant t = Instant.now();
        List<DomainEvent> events = List.of(
                new VnfInstantiationStarted(VNF_ID_STR, "res", 1, t),
                new VnfInstantiated(VNF_ID_STR, "vim-456", "192.168.1.10", 2, t)
        );

        VnfAggregate rebuilt = VnfAggregate.from(events);

        assertThat(rebuilt.getVnfId()).isEqualTo(VNF_ID);
        assertThat(rebuilt.getState()).isEqualTo(VnfState.ACTIVE);
        assertThat(rebuilt.getVersion()).isEqualTo(2);
        assertThat(rebuilt.getVimResourceId()).isEqualTo("vim-456");
        assertThat(rebuilt.getIpAddress()).isEqualTo("192.168.1.10");
    }

    @Test
    void from_emptyList_yieldsInitialStateWithNullVnfId() {
        VnfAggregate rebuilt = VnfAggregate.from(List.of());

        assertThat(rebuilt.getVnfId()).isNull();
        assertThat(rebuilt.getState()).isEqualTo(VnfState.INITIAL);
        assertThat(rebuilt.getVersion()).isEqualTo(0);
    }

    @Test
    void from_fullLifecycleToTerminated_yieldsTerminatedState() {
        Instant t = Instant.now();
        List<DomainEvent> events = List.of(
                new VnfInstantiationStarted(VNF_ID_STR, "res", 1, t),
                new VnfInstantiated(VNF_ID_STR, "vim-1", "10.0.0.1", 2, t),
                new VnfTerminationStarted(VNF_ID_STR, 3, t),
                new VnfTerminated(VNF_ID_STR, 4, t)
        );

        VnfAggregate rebuilt = VnfAggregate.from(events);

        assertThat(rebuilt.getState()).isEqualTo(VnfState.TERMINATED);
        assertThat(rebuilt.getVersion()).isEqualTo(4);
    }

    @Test
    void process_vimResourcesAllocatedEvent_throwsUnsupported() {
        VnfAggregate agg = VnfAggregate.create(VNF_ID);
        VimResourcesAllocatedEvent event = new VimResourcesAllocatedEvent(VNF_ID_STR, "vim-1", "10.0.0.1");

        assertThatThrownBy(() -> agg.process(event))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not yet implemented");
    }
}
