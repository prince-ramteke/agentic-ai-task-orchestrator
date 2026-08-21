package com.prince.agentic.tool.task;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskCreateToolTest extends AbstractToolContractTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskCreateTool toolUnderTest = new TaskCreateTool(taskService);

    @Override
    protected Tool<?, ?> tool() {
        return toolUnderTest;
    }

    @Test
    void descriptor_is_side_effecting_task_create() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("task.create");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.SIDE_EFFECTING);
        assertThat(toolUnderTest.descriptor().inputType()).isEqualTo(TaskCreateRequest.class);
    }

    @Test
    void execute_passes_principal_and_request_to_service() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        TaskCreateRequest req = new TaskCreateRequest("via tool", null, null, TaskPriority.HIGH, null, null);
        TaskResponse expected = mock(TaskResponse.class);
        when(taskService.create(eq(user), eq(req))).thenReturn(expected);

        assertThat(toolUnderTest.execute(ctx, req)).isSameAs(expected);
        verify(taskService).create(user, req);
    }
}
