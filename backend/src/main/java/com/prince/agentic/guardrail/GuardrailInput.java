package com.prince.agentic.guardrail;

import com.prince.agentic.agent.AgentDecision;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolDescriptor;

/**
 * The complete, backend-assembled input a {@link GuardrailPolicy} evaluates (spec §4). The
 * {@code descriptor} is resolved by the engine from the {@code ToolRegistry} — the authoritative
 * source of a tool's risk — so a policy never trusts a model-supplied risk. {@code principal} is the
 * verified identity; policies must derive authority from it, never from any text field.
 *
 * @param principal  the authenticated caller
 * @param decision   the validated model decision (tool + arguments)
 * @param descriptor the resolved tool descriptor (authoritative risk/metadata)
 * @param ctx        backend correlation only
 */
public record GuardrailInput(
        AuthenticatedUser principal,
        AgentDecision decision,
        ToolDescriptor descriptor,
        GuardrailContext ctx) {
}
