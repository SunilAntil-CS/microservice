package com.telecom.vnfm.lcm.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VnfInstanceRepository extends JpaRepository<VnfInstance, String> {

    @Query("SELECT v FROM VnfInstance v WHERE v.state = :state AND v.updatedAt < :cutoff")
    List<VnfInstance> findByStateAndUpdatedAtBefore(
            @Param("state") VnfInstance.VnfState state,
            @Param("cutoff") Instant cutoff);
}
