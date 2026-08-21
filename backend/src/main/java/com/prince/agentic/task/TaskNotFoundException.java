package com.prince.agentic.task;

import com.prince.agentic.common.exception.ResourceNotFoundException;

/**
 * A task does not exist OR is not visible to the caller. Both cases render as 404 so the API
 * never reveals whether a task id exists to a non-owner (existence-masking; see ADR-0006).
 */
public class TaskNotFoundException extends ResourceNotFoundException {
    public TaskNotFoundException(Long id) {
        super("Task not found: " + id);
    }
}
