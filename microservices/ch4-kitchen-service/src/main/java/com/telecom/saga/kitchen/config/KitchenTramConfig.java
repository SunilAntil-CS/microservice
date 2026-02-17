package com.telecom.saga.kitchen.config;

import com.telecom.saga.kitchen.saga.KitchenSagaCommandHandler;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Kitchen Service (saga participant) command handlers with Eventuate Tram.
 */
@Configuration
public class KitchenTramConfig {

    @Bean
    public CommandHandlers kitchenCommandHandlers(KitchenSagaCommandHandler handler) {
        return handler.commandHandlers();
    }
}
