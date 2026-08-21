package com.prince.agentic.tool.task;

import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code task.create} — create a task owned by the caller. Reuses {@link TaskCreateRequest}, which has
 * no {@code ownerId} field, so ownership is assigned server-side and cannot be spoofed. SIDE_EFFECTING
 * (M8 will add confirmation/guardrail treatment on top of this classification).
 */
@Component
public class TaskCreateTool implements Tool<TaskCreateRequest, TaskResponse> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "task.create", "Create a new task owned by the current user.", "task", "1",
            ToolRiskLevel.SIDE_EFFECTING, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            TaskCreateRequest.class, TaskResponse.class, Duration.ofSeconds(10));

    private final TaskService taskService;

    public TaskCreateTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public TaskResponse execute(ToolExecutionContext context, TaskCreateRequest input) {
        return taskService.create(context.principal(), input);
    }
}
