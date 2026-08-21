package com.prince.agentic.tool.task;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskSummaryResponse;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code task.search} — list the caller's tasks with bounded filters. Results are always own-scoped
 * (the service enforces ownership). Sort is fixed to the service default in M5. READ_ONLY.
 */
@Component
public class TaskSearchTool implements Tool<TaskSearchInput, PageResponse<TaskSummaryResponse>> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "task.search", "Search the current user's tasks by status, priority, and due date.",
            "task", "1", ToolRiskLevel.READ_ONLY, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            TaskSearchInput.class, PageResponse.class, Duration.ofSeconds(10));

    private final TaskService taskService;

    public TaskSearchTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PageResponse<TaskSummaryResponse> execute(ToolExecutionContext context, TaskSearchInput input) {
        return taskService.list(context.principal(), input.status(), input.priority(), input.dueBefore(),
                input.page(), input.size(), null);   // null sort → service default (no model-supplied sort)
    }
}
