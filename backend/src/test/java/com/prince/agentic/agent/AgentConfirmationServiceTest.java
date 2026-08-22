package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.guardrail.RateLimiter;
import com.prince.agentic.guardrail.confirmation.ConfirmationService;
import com.prince.agentic.guardrail.confirmation.ConfirmedAction;
import com.prince.agentic.guardrail.exception.RateLimitedException;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolExecutor;
import com.prince.agentic.tool.ToolResult;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentConfirmationServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser(7L, "u@b.com", Set.of("ROLE_USER"));
    private final ConfirmationService confirmations = mock(ConfirmationService.class);
    private final ToolExecutor executor = mock(ToolExecutor.class);
    private final ObservationSerializer observations =
            new ObservationSerializer(new ObjectMapper(), new AgentProperties(8, 10, 60, 2, 2000, 20));

    private AgentConfirmationService service(RateLimiter limiter) {
        return new AgentConfirmationService(confirmations, limiter, executor, observations);
    }

    @Test
    void confirm_executesExactStoredActionOnce_andReturnsSafeSummary() {
        ConfirmedAction stored = new ConfirmedAction("task.create",
                Map.of("title", "review"), ToolRiskLevel.SIDE_EFFECTING);
        when(confirmations.confirm(user, "conf-1")).thenReturn(stored);
        when(executor.execute(eq("task.create"), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.create", Map.of("id", 42), 5));

        AgentConfirmationOutcome outcome = service(u -> true).confirm(user, "conf-1");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.tool()).isEqualTo("task.create");
        assertThat(outcome.resultSummary()).contains("42");
        // Exactly the stored arguments are executed, exactly once.
        verify(executor, times(1)).execute(eq("task.create"), eq(Map.of("title", "review")), any());
    }

    @Test
    void confirm_whenRateLimited_throws_andNeitherConsumesNorExecutes() {
        assertThatThrownBy(() -> service(u -> false).confirm(user, "conf-1"))
                .isInstanceOf(RateLimitedException.class);
        verifyNoInteractions(confirmations);
        verify(executor, never()).execute(any(), anyMap(), any());
    }

    @Test
    void cancel_delegatesToConfirmationService() {
        service(u -> true).cancel(user, "conf-1");
        verify(confirmations).cancel(user, "conf-1");
    }
}
