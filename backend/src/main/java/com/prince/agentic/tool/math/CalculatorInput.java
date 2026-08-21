package com.prince.agentic.tool.math;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Input for {@code math.calculate}. Bounded to protect the executor from abusive model arguments. */
public record CalculatorInput(

        @NotBlank
        @Size(max = 256)
        String expression) {}
