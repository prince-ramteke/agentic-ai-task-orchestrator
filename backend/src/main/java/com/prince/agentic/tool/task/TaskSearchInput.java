package com.prince.agentic.tool.task;

import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/**
 * Bounded filters for {@code task.search}. Sort is intentionally not exposed in M5 (least privilege);
 * the service applies its default sort. Results are always the caller's own (ownership in the service).
 */
public record TaskSearchInput(
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueBefore,
        @PositiveOrZero Integer page,
        @Min(1) @Max(100) Integer size) {}
