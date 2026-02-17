package com.telecom.saga.kitchen.service;

import com.telecom.saga.common.command.CancelCreateTicketCommand;
import com.telecom.saga.common.command.CreateTicketCommand;
import com.telecom.saga.common.reply.CreateTicketReply;
import com.telecom.saga.kitchen.domain.Ticket;
import com.telecom.saga.kitchen.domain.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SAGA PARTICIPANT - Handles commands from the orchestrator (Order Service).
 * ---------------------------------------------------------------------------
 * CONCEPT: Each method is one local transaction. There is no distributed TX.
 * createTicket: INSERT ticket (and line items). If this fails, the orchestrator
 *   will not run compensation (because the forward step did not commit).
 * cancelTicket: UPDATE ticket to CANCELLED. This is the compensating transaction
 *   when a later saga step fails; the orchestrator calls this to "undo" the
 *   create. Idempotency: cancelling an already-cancelled ticket is safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenCommandService {

    private final TicketRepository ticketRepository;

    /**
     * Forward step: create a kitchen ticket. Returns reply with ticketId so the
     * orchestrator can store it for possible compensation.
     */
    @Transactional
    public CreateTicketReply createTicket(CreateTicketCommand command) {
        Ticket ticket = Ticket.create(
                command.getOrderId(),
                command.getRestaurantId(),
                command.getLineItems()
        );
        ticket = ticketRepository.save(ticket);
        log.info("Created ticket id={} for orderId={}", ticket.getId(), command.getOrderId());
        return CreateTicketReply.builder()
                .ticketId(ticket.getId())
                .orderId(command.getOrderId())
                .build();
    }

    /**
     * Compensating step: cancel the ticket. Called by the orchestrator when
     * a later step (e.g. accounting) fails. Idempotent: multiple cancel calls
     * for the same ticket are safe (state stays CANCELLED).
     */
    @Transactional
    public void cancelTicket(CancelCreateTicketCommand command) {
        Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + command.getTicketId()));
        ticket.cancel();
        ticketRepository.save(ticket);
        log.info("Cancelled ticket id={} for orderId={} (compensation)", ticket.getId(), command.getOrderId());
    }
}
