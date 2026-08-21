package com.prince.agentic.task.dto;

import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;

import java.time.LocalDate;

/** Lightweight list view of a task (collection endpoints). */
public record TaskSummaryResponse(
        Long id,
        String title,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate) {
}
