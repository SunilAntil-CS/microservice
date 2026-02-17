package com.telecom.vnfm.lcm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for the VnfInstance aggregate.
 * ---------------------------------------------------------------------------
 * DDD ROLE: Repositories provide the illusion of an in-memory collection of
 * aggregates. We load by id (vnfId), save after domain operations, and do not
 * expose the underlying persistence details to the domain. This repository
 * lives in the LCM service and accesses only the LCM database.
 */
public interface VnfInstanceRepository extends JpaRepository<VnfInstance, String> {

    /**
     * Used by the Timeout Watchdog: find instances stuck in DEPLOYING_INFRA
     * longer than the given cutoff (e.g. 15 minutes). Choreography mitigation—
     * without a central orchestrator, we use a scheduled job to detect "lost" flows.
     */
    @Query("SELECT v FROM VnfInstance v WHERE v.state = :state AND v.updatedAt < :cutoff")
    List<VnfInstance> findByStateAndUpdatedAtBefore(
            @Param("state") VnfInstance.VnfState state,
            @Param("cutoff") Instant cutoff);
}
