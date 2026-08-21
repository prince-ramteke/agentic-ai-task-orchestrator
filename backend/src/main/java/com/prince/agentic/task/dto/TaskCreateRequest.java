package com.prince.agentic.task.dto;

import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create a task. NO owner field — ownership is assigned server-side from the authenticated
 * principal (any client-supplied ownerId is an unknown property and ignored). status/priority
 * are optional on create and default to TODO/MEDIUM in the service. dueDate is unconstrained
 * (past dates are valid — e.g. importing overdue tasks).
 */
public record TaskCreateRequest(

        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        @PositiveOrZero(message = "estimatedHours must be zero or positive")
        @Digits(integer = 4, fraction = 2, message = "estimatedHours must have at most 4 integer and 2 fraction digits")
        BigDecimal estimatedHours,

        LocalDate dueDate) {
}
