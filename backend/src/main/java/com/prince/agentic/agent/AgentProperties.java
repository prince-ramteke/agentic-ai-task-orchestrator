package com.prince.agentic.agent;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Env-tunable, positive agent execution bounds (spec D14): max planner iterations, max tool
 * calls, wall-clock timeout, loop-detection threshold, and observation-serialization caps.
 *
 * <p>Bound via {@code agent.*} keys (see {@code AgentConfig}); overridable via
 * {@code AGENT_MAX_ITERATIONS}, {@code AGENT_MAX_TOOL_CALLS}, {@code AGENT_TIMEOUT_SECONDS},
 * {@code AGENT_LOOP_THRESHOLD}, {@code AGENT_MAX_OBSERVATION_CHARS}, {@code AGENT_MAX_ARRAY_ITEMS}.
 * Zero/unset values fall back to the defaults below so a minimal environment still binds cleanly.
 */
@Validated
@ConfigurationProperties("agent")
public record AgentProperties(
        @Min(1) int maxIterations,
        @Min(1) int maxToolCalls,
        @Min(1) int timeoutSeconds,
        @Min(1) int loopThreshold,
        @Min(1) int maxObservationChars,
        @Min(1) int maxArrayItems) {

    public AgentProperties {
        if (maxIterations == 0) maxIterations = 8;
        if (maxToolCalls == 0) maxToolCalls = 10;
        if (timeoutSeconds == 0) timeoutSeconds = 60;
        if (loopThreshold == 0) loopThreshold = 2;
        if (maxObservationChars == 0) maxObservationChars = 2000;
        if (maxArrayItems == 0) maxArrayItems = 20;
    }
}
