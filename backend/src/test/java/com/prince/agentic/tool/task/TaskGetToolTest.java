package com.prince.agentic.tool.task;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.task.TaskService;
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

class TaskGetToolTest extends AbstractToolContractTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskGetTool toolUnderTest = new TaskGetTool(taskService);

    @Override
    protected Tool<?, ?> tool() {
        return toolUnderTest;
    }

    @Test
    void descriptor_is_read_only_task_get() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("task.get");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
    }

    @Test
    void execute_passes_principal_and_id_to_service() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        TaskResponse expected = mock(TaskResponse.class);
        when(taskService.get(eq(user), eq(42L))).thenReturn(expected);

        assertThat(toolUnderTest.execute(ctx, new TaskGetInput(42L))).isSameAs(expected);
        verify(taskService).get(user, 42L);
    }
}
