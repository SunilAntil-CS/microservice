package com.telecom.saga.accounting.config;

import com.telecom.saga.accounting.saga.AccountingSagaCommandHandler;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Accounting Service (saga participant - pivot) command handlers.
 */
@Configuration
public class AccountingTramConfig {

    @Bean
    public CommandHandlers accountingCommandHandlers(AccountingSagaCommandHandler handler) {
        return handler.commandHandlers();
    }
}
