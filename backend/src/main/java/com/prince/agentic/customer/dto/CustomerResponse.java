package com.prince.agentic.customer.dto;

import com.prince.agentic.customer.CustomerStatus;

import java.time.Instant;

/** Detail view of a customer. */
public record CustomerResponse(
        Long id,
        Long ownerId,
        String name,
        String email,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
