package com.telecom.saga.kitchen.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for Ticket.
 * All Kitchen persistence is local to this service's DB.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
