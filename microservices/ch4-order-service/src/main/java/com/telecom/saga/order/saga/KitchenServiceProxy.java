package com.telecom.saga.order.saga;

import com.telecom.saga.common.command.CancelCreateTicketCommand;
import com.telecom.saga.common.command.CreateTicketCommand;
import com.telecom.saga.common.reply.CreateTicketReply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * PROXY: Outbound calls from Order Service to Kitchen Service.
 * ---------------------------------------------------------------------------
 * CONCEPT: The orchestrator never talks to the participant's database. It
 * sends commands via HTTP (here) or via messages (Kafka/Eventuate in production).
 * This proxy hides the transport and keeps the saga definition clean.
 * ---------------------------------------------------------------------------
 * PRODUCTION: Use resilience4j @CircuitBreaker and timeout here so that a
 * slow or down Kitchen service does not block the orchestrator indefinitely.
 */
@Component
public class KitchenServiceProxy {

    private final WebClient webClient;
    private final String kitchenServiceUrl;

    public KitchenServiceProxy(WebClient.Builder builder,
                               @Value("${kitchen.service.url:http://localhost:8082}") String kitchenServiceUrl) {
        this.webClient = builder.build();
        this.kitchenServiceUrl = kitchenServiceUrl;
    }

    /**
     * Forward step: ask Kitchen to create a ticket. Returns reply or throws.
     */
    public CreateTicketReply createTicket(CreateTicketCommand command) {
        try {
            return webClient.post()
                    .uri(kitchenServiceUrl + "/api/tickets/create")
                    .bodyValue(command)
                    .retrieve()
                    .bodyToMono(CreateTicketReply.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new SagaStepFailedException("Kitchen createTicket failed: " + e.getStatusCode(), e);
        }
    }

    /**
     * Compensating step: ask Kitchen to cancel the ticket.
     */
    public void cancelTicket(CancelCreateTicketCommand command) {
        try {
            webClient.post()
                    .uri(kitchenServiceUrl + "/api/tickets/cancel")
                    .bodyValue(command)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            // Log and optionally retry; for learning we rethrow so orchestrator can log
            throw new SagaStepFailedException("Kitchen cancelTicket failed: " + e.getStatusCode(), e);
        }
    }
}
