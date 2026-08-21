package com.prince.agentic.customer.dto;

import com.prince.agentic.customer.CustomerStatus;

/** Lightweight list view of a customer. */
public record CustomerSummaryResponse(
        Long id,
        String name,
        String email,
        CustomerStatus status) {
}
