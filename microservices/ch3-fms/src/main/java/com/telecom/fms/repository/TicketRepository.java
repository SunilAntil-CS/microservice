package com.telecom.fms.repository;

import com.telecom.fms.model.TroubleTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<TroubleTicket, String> {

    List<TroubleTicket> findByNodeIdOrderByCreatedAtDesc(String nodeId);
}
