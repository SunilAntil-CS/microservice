package com.telecom.saga.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: One line item in a kitchen ticket (menu item + quantity).
 * Shared between Order and Kitchen so both sides agree on the same structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLineItem {

    private String menuItemId;
    private int quantity;
}
