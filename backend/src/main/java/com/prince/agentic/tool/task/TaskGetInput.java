package com.prince.agentic.tool.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Input for {@code task.get}. Carries only the argument — never identity. */
public record TaskGetInput(@NotNull @Positive Long taskId) {}
