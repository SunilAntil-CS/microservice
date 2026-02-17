package com.telecom.saga.kitchen.api;

import com.telecom.saga.common.command.CancelCreateTicketCommand;
import com.telecom.saga.common.command.CreateTicketCommand;
import com.telecom.saga.common.reply.CreateTicketReply;
import com.telecom.saga.kitchen.service.KitchenCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * REST endpoint that receives Saga commands from the Order Service (orchestrator).
 * ---------------------------------------------------------------------------
 * CONCEPT: In a messaging setup (Eventuate Tram / Kafka), the orchestrator
 * would send commands to a channel (e.g. "kitchenService") and this service
 * would consume from that channel. The contract (CreateTicketCommand, etc.)
 * is the same; only the transport differs (HTTP here for simplicity).
 * ---------------------------------------------------------------------------
 * Production: These endpoints would be internal-only (not exposed to the
 * public API gateway). Alternatively, use message-driven command handlers.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketCommandController {

    private final KitchenCommandService kitchenCommandService;

    /**
     * Forward command: create a ticket. Called by the saga orchestrator.
     */
    @PostMapping("/create")
    public ResponseEntity<CreateTicketReply> createTicket(@Valid @RequestBody CreateTicketCommand command) {
        CreateTicketReply reply = kitchenCommandService.createTicket(command);
        return ResponseEntity.ok(reply);
    }

    /**
     * Compensating command: cancel a ticket. Called when the saga fails after
     * create and the orchestrator runs compensation.
     */
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelTicket(@Valid @RequestBody CancelCreateTicketCommand command) {
        kitchenCommandService.cancelTicket(command);
        return ResponseEntity.ok().build();
    }
}
