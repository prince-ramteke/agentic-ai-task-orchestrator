package com.prince.agentic.task.dto;

import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Detail view of a task (single-resource endpoints). */
public record TaskResponse(
        Long id,
        Long ownerId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        BigDecimal estimatedHours,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt) {
}
