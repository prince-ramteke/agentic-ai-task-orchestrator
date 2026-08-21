package com.prince.agentic.tool.task;

import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.TaskStatus;
import com.prince.agentic.task.dto.TaskSummaryResponse;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSearchToolTest extends AbstractToolContractTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskSearchTool toolUnderTest = new TaskSearchTool(taskService);

    @Override
    protected Tool<?, ?> tool() {
        return toolUnderTest;
    }

    @Test
    void descriptor_is_read_only_task_search() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("task.search");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_forwards_filters_and_uses_default_sort() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        LocalDate due = LocalDate.of(2026, 1, 1);
        PageResponse<TaskSummaryResponse> expected = mock(PageResponse.class);
        when(taskService.list(eq(user), eq(TaskStatus.TODO), eq(TaskPriority.HIGH), eq(due),
                eq(0), eq(20), isNull())).thenReturn(expected);

        PageResponse<TaskSummaryResponse> out = toolUnderTest.execute(ctx,
                new TaskSearchInput(TaskStatus.TODO, TaskPriority.HIGH, due, 0, 20));

        assertThat(out).isSameAs(expected);
        verify(taskService).list(user, TaskStatus.TODO, TaskPriority.HIGH, due, 0, 20, null);
    }
}
