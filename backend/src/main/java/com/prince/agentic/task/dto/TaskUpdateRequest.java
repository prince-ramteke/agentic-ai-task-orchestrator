package com.prince.agentic.task.dto;

import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Full-replacement update (PUT). status and priority are REQUIRED so a full replacement can never
 * silently reset the lifecycle fields to a default; omitting description/estimatedHours/dueDate
 * clears them to null.
 */
public record TaskUpdateRequest(

        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotNull(message = "status is required")
        TaskStatus status,

        @NotNull(message = "priority is required")
        TaskPriority priority,

        @PositiveOrZero(message = "estimatedHours must be zero or positive")
        @Digits(integer = 4, fraction = 2, message = "estimatedHours must have at most 4 integer and 2 fraction digits")
        BigDecimal estimatedHours,

        LocalDate dueDate) {
}
