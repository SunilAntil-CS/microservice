package com.vnfm.lcm.infrastructure.eventstore;

import com.vnfm.lcm.domain.DomainEvent;
import com.vnfm.lcm.domain.event.VnfInstantiated;
import com.vnfm.lcm.domain.event.VnfInstantiationStarted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.vnfm.lcm.infrastructure.eventstore.EventStore.AGGREGATE_TYPE_VNF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the event store using @DataJpaTest and H2.
 * Verifies save/load and optimistic locking.
 */
@DataJpaTest
@ActiveProfiles("test")
@EntityScan(basePackages = "com.vnfm.lcm.infrastructure.eventstore")
@EnableJpaRepositories(basePackages = "com.vnfm.lcm.infrastructure.eventstore")
@Import({DomainEventSerializer.class, JdbcEventStore.class})
class JdbcEventStoreIntegrationTest {

    @Autowired
    EventStore eventStore;

    @Autowired
    SnapshotEntityRepository snapshotRepository;

    private static final UUID AGG_ID = UUID.randomUUID();
    private static final String AGG_ID_STR = AGG_ID.toString();

    @Test
    void saveEvents_and_loadEvents_roundTrip() {
        VnfInstantiationStarted e1 = new VnfInstantiationStarted(AGG_ID_STR, "res1", 1, Instant.now());
        VnfInstantiated e2 = new VnfInstantiated(AGG_ID_STR, "vim-1", "10.0.0.1", 2, Instant.now());

        eventStore.saveEvents(AGG_ID, AGGREGATE_TYPE_VNF, List.of(e1, e2), 0);

        List<DomainEvent> loaded = eventStore.loadEvents(AGG_ID, AGGREGATE_TYPE_VNF);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0)).isInstanceOf(VnfInstantiationStarted.class);
        assertThat(((VnfInstantiationStarted) loaded.get(0)).getResources()).isEqualTo("res1");
        assertThat(loaded.get(1)).isInstanceOf(VnfInstantiated.class);
        assertThat(((VnfInstantiated) loaded.get(1)).getVimResourceId()).isEqualTo("vim-1");
        assertThat(((VnfInstantiated) loaded.get(1)).getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void saveEvents_optimisticLock_failsWhenVersionMismatch() {
        VnfInstantiationStarted e1 = new VnfInstantiationStarted(AGG_ID_STR, "res", 1, Instant.now());
        eventStore.saveEvents(AGG_ID, AGGREGATE_TYPE_VNF, List.of(e1), 0);

        VnfInstantiated e2 = new VnfInstantiated(AGG_ID_STR, "vim-1", "10.0.0.1", 2, Instant.now());
        assertThatThrownBy(() -> eventStore.saveEvents(AGG_ID, AGGREGATE_TYPE_VNF, List.of(e2), 0))
                .isInstanceOf(OptimisticLockingException.class)
                .satisfies(ex -> {
                    OptimisticLockingException o = (OptimisticLockingException) ex;
                    assertThat(o.getExpectedVersion()).isEqualTo(0);
                    assertThat(o.getActualVersion()).isEqualTo(1);
                });
    }

    @Test
    void saveEvents_optimisticLock_succeedsWhenVersionMatches() {
        VnfInstantiationStarted e1 = new VnfInstantiationStarted(AGG_ID_STR, "res", 1, Instant.now());
        eventStore.saveEvents(AGG_ID, AGGREGATE_TYPE_VNF, List.of(e1), 0);

        VnfInstantiated e2 = new VnfInstantiated(AGG_ID_STR, "vim-1", "10.0.0.1", 2, Instant.now());
        eventStore.saveEvents(AGG_ID, AGGREGATE_TYPE_VNF, List.of(e2), 1);

        List<DomainEvent> loaded = eventStore.loadEvents(AGG_ID, AGGREGATE_TYPE_VNF);
        assertThat(loaded).hasSize(2);
    }

    @Test
    void loadEvents_emptyAggregate_returnsEmptyList() {
        List<DomainEvent> loaded = eventStore.loadEvents(UUID.randomUUID(), AGGREGATE_TYPE_VNF);
        assertThat(loaded).isEmpty();
    }

    @Test
    void getLatestSnapshot_empty_returnsEmpty() {
        assertThat(eventStore.getLatestSnapshot(AGG_ID, AGGREGATE_TYPE_VNF)).isEmpty();
    }

    @Test
    void getLatestSnapshot_afterSavingSnapshot_returnsIt() {
        SnapshotEntity s = new SnapshotEntity(AGG_ID_STR, 2, "{\"state\":\"ACTIVE\"}", Instant.now());
        snapshotRepository.save(s);

        assertThat(eventStore.getLatestSnapshot(AGG_ID, AGGREGATE_TYPE_VNF)).isPresent()
                .get()
                .satisfies(snap -> {
                    assertThat(snap.aggregateId()).isEqualTo(AGG_ID);
                    assertThat(snap.version()).isEqualTo(2);
                    assertThat(snap.payload()).isEqualTo("{\"state\":\"ACTIVE\"}");
                });
    }
}
