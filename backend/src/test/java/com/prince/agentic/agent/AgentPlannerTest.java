package com.prince.agentic.agent;

import com.prince.agentic.agent.exception.AgentInvalidDecisionException;
import com.prince.agentic.agent.support.ScriptedLlmClient;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.tool.ToolRegistry;
import com.prince.agentic.tool.task.TaskSearchTool;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentPlannerTest {

    private final ToolRegistry registry =
            new ToolRegistry(List.of(new TaskSearchTool(mock(TaskService.class))));
    private final AgentToolCatalog catalog = new AgentToolCatalog(registry);
    private final AgentPromptService prompts = new AgentPromptService();
    private final AgentDecisionValidator validator = new AgentDecisionValidator();

    @Test
    void returns_validDecision_onFirstAttempt() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .enqueueStructured(new AgentDecision(AgentAction.FINAL, "hi", null, null));
        AgentPlanner planner = new AgentPlanner(llm, prompts, validator, catalog);
        AgentDecision d = planner.decide("hello", "(none)", List.of(), 8, 10);
        assertThat(d.action()).isEqualTo(AgentAction.FINAL);
    }

    @Test
    void rendersBoundedHistory_intoThePrompt() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .enqueueStructured(new AgentDecision(AgentAction.FINAL, "ok", null, null));
        AgentPlanner planner = new AgentPlanner(llm, prompts, validator, catalog);
        planner.decide("which is due first?", "USER: show my high priority tasks", List.of(), 8, 10);
        assertThat(llm.prompts()).hasSize(1);
        assertThat(llm.prompts().get(0)).contains("USER: show my high priority tasks");
    }

    @Test
    void repairs_once_thenSucceeds() {
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.FINAL, null, null, null),               // invalid
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of()) // valid
        );
        AgentPlanner planner = new AgentPlanner(llm, prompts, validator, catalog);
        AgentDecision d = planner.decide("show tasks", "(none)", List.of(), 8, 10);
        assertThat(d.action()).isEqualTo(AgentAction.TOOL_CALL);
        assertThat(llm.prompts()).hasSize(2); // one repair
    }

    @Test
    void throws_afterRepairAlsoInvalid() {
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.FINAL, null, null, null),
                new AgentDecision(AgentAction.FINAL, null, null, null));
        AgentPlanner planner = new AgentPlanner(llm, prompts, validator, catalog);
        assertThatThrownBy(() -> planner.decide("x", "(none)", List.of(), 8, 10))
                .isInstanceOf(AgentInvalidDecisionException.class);
    }
}
