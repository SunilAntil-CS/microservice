package com.telecom.saga.order.config;

import com.telecom.saga.order.command.OrderCommandHandler;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Order Service command handlers with Eventuate Tram.
 * The "orderService" channel receives RejectOrderCommand (saga compensation).
 */
@Configuration
public class OrderTramConfig {

    @Bean
    public CommandHandlers orderCommandHandlers(OrderCommandHandler orderCommandHandler) {
        return orderCommandHandler.commandHandlers();
    }
}
