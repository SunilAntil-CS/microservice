package com.telecom.saga.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ACCOUNTING SERVICE - Saga Participant (Pivot Transaction).
 * ---------------------------------------------------------------------------
 * SAGA ROLE: Handles AuthorizeCardCommand from the Order Service. This is the
 * PIVOT step: if authorization fails (e.g. card expired or total &lt; 0), the
 * saga triggers compensation (cancel ticket, reject order).
 */
@SpringBootApplication
public class AccountingSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingSagaApplication.class, args);
    }
}
