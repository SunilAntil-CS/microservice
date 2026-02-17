package com.vnfm.lcm.infrastructure.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SagaTimeoutRepository extends JpaRepository<SagaTimeout, Long> {

    /** Fetch all unprocessed timeouts that are due (executeAt <= now). */
    List<SagaTimeout> findByProcessedFalseAndExecuteAtLessThanEqualOrderByExecuteAtAsc(Instant now);

    /** Find timeouts for a saga+step (to mark processed when reply arrives). */
    List<SagaTimeout> findBySagaIdAndStepAndProcessedFalse(String sagaId, int step);
}
