package com.vnfm.lcm.infrastructure.readside;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VnfIndexRepository extends JpaRepository<VnfIndex, String> {

    List<VnfIndex> findAllByOrderByCreatedAtDesc();
}
