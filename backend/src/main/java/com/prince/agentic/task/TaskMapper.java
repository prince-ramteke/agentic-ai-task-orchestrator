package com.prince.agentic.task;

import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.task.dto.TaskSummaryResponse;

/** Entity → DTO mapping. Static and stateless: no owner navigation, no lazy loading. */
public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(), t.getOwnerId(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getEstimatedHours(), t.getDueDate(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    public static TaskSummaryResponse toSummary(Task t) {
        return new TaskSummaryResponse(
                t.getId(), t.getTitle(), t.getStatus(), t.getPriority(), t.getDueDate());
    }
}
