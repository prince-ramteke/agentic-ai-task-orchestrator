package com.prince.agentic.tool.task;

import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code task.get} — fetch one of the caller's tasks by id. Ownership, 404-masking, and
 * admin-any-by-id are enforced by {@link TaskService} (the tool only supplies the authenticated
 * principal and maps the typed result). READ_ONLY.
 */
@Component
public class TaskGetTool implements Tool<TaskGetInput, TaskResponse> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "task.get", "Get one task owned by the current user, by id.", "task", "1",
            ToolRiskLevel.READ_ONLY, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            TaskGetInput.class, TaskResponse.class, Duration.ofSeconds(10));

    private final TaskService taskService;

    public TaskGetTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public TaskResponse execute(ToolExecutionContext context, TaskGetInput input) {
        return taskService.get(context.principal(), input.taskId());
    }
}
