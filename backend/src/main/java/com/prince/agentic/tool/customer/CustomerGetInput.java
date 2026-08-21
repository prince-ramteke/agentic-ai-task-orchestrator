package com.prince.agentic.tool.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Input for {@code customer.get}. Carries only the argument — never identity. */
public record CustomerGetInput(@NotNull @Positive Long customerId) {}
