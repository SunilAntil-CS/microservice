package com.vnfm.lcm.infrastructure.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    Optional<SagaInstance> findBySagaId(String sagaId);
}
