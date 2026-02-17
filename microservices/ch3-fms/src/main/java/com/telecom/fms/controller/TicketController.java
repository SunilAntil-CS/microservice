package com.telecom.fms.controller;

import com.telecom.fms.model.TroubleTicket;
import com.telecom.fms.repository.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * List trouble tickets (e.g. to verify idempotency: after sending same messageId twice, only one ticket).
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping
    public List<TroubleTicket> list() {
        return ticketRepository.findAll();
    }
}
