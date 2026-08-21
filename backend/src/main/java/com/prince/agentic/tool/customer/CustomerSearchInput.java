package com.prince.agentic.tool.customer;

import com.prince.agentic.customer.CustomerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Bounded filters for {@code customer.search} — the same filters M3's list endpoint provides.
 * Sort is not exposed in M5 (service default). Results are always own-scoped (service enforces it).
 */
public record CustomerSearchInput(
        CustomerStatus status,
        @Size(max = 200) String search,
        @PositiveOrZero Integer page,
        @Min(1) @Max(100) Integer size) {}
