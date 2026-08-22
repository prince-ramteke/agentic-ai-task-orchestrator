package com.prince.agentic.guardrail;

import com.prince.agentic.agent.AgentDecision;
import com.prince.agentic.security.AuthenticatedUser;

/**
 * The authoritative policy boundary between a validated {@link AgentDecision} and any effect
 * (spec §4). Called by the orchestrator before {@code ToolExecutor.execute}; it decides ALLOW /
 * DENY / REQUIRE_CONFIRMATION. It adds policy only — it never resolves, authenticates, binds,
 * validates, or authorizes a tool (those remain in {@code ToolExecutor} and run exactly once).
 */
public interface GuardrailEngine {

    GuardrailDecision evaluate(AuthenticatedUser principal, AgentDecision decision, GuardrailContext ctx);
}
