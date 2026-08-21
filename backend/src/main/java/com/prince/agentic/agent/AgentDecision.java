package com.prince.agentic.agent;

import java.util.Map;

/**
 * The LLM's typed decision (spec §5). Target type for {@code LlmClient.generateStructured}.
 * FINAL uses {@code response}; TOOL_CALL uses {@code tool} + {@code arguments}. Validated by
 * {@link AgentDecisionValidator} before the orchestrator acts on it.
 */
public record AgentDecision(
        AgentAction action,
        String response,
        String tool,
        Map<String, Object> arguments) {
}
