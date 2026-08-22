package com.prince.agentic.guardrail;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Env-tunable guardrail bounds (spec §15). Bound via {@code guardrail.*}; overridable via
 * {@code AGENT_CONFIRMATION_TTL_SECONDS}, {@code AGENT_USER_TOOL_BUDGET_PER_MIN},
 * {@code AGENT_MAX_ARGUMENT_CHARS}. Zero/unset values fall back to the defaults below so a minimal
 * environment still binds cleanly (mirrors {@code MemoryProperties}/{@code AgentProperties}).
 *
 * @param confirmationTtlSeconds  how long a pending side-effect confirmation stays valid
 * @param userToolBudgetPerMin    per-user fixed-window tool-call budget (executions per minute)
 * @param maxArgumentChars        max serialized argument length a policy will admit before DENY
 */
@Validated
@ConfigurationProperties("guardrail")
public record GuardrailProperties(
        @Min(1) int confirmationTtlSeconds,
        @Min(1) int userToolBudgetPerMin,
        @Min(1) int maxArgumentChars) {

    public GuardrailProperties {
        if (confirmationTtlSeconds == 0) confirmationTtlSeconds = 300;
        if (userToolBudgetPerMin == 0) userToolBudgetPerMin = 60;
        if (maxArgumentChars == 0) maxArgumentChars = 4000;
    }
}
