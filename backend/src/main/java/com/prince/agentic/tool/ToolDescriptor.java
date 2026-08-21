package com.prince.agentic.tool;

import java.time.Duration;
import java.util.Set;

/**
 * Immutable metadata for a {@link Tool}. This is what the future agent (M6) uses to understand the
 * available capabilities, and what the {@link ToolRegistry}/{@link ToolExecutor} enforce.
 *
 * <p>The compact constructor validates the invariants so an invalid descriptor cannot exist (and,
 * via the registry, cannot start the application). {@code inputType}/{@code outputType} are the
 * concrete Java types; M6's Spring AI adapter derives a JSON schema from {@code inputType} — M5
 * deliberately builds no schema engine.
 *
 * @param name                   unique, stable, dot-namespaced identity (e.g. {@code task.get}) — never a Java class name
 * @param description            model-facing purpose
 * @param category               grouping (e.g. {@code task}, {@code customer}, {@code math})
 * @param version                descriptor version (default {@code "1"}); not encoded in the name
 * @param risk                   side-effect/risk classification
 * @param requiresAuthentication whether an authenticated principal is required (M5: always true)
 * @param requiredRoles          roles that may use this tool (tool-type authorization), e.g. {@code ROLE_USER}
 * @param inputType              concrete input type (bound + validated before execution)
 * @param outputType             concrete output type (a safe DTO/result model)
 * @param timeout                declared max execution time (metadata + measured in M5; hard-enforced in M8)
 */
public record ToolDescriptor(
        String name,
        String description,
        String category,
        String version,
        ToolRiskLevel risk,
        boolean requiresAuthentication,
        Set<String> requiredRoles,
        Class<?> inputType,
        Class<?> outputType,
        Duration timeout) {

    public ToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("tool description is required");
        }
        if (risk == null) {
            throw new IllegalArgumentException("tool risk is required");
        }
        if (requiredRoles == null) {
            throw new IllegalArgumentException("requiredRoles must not be null");
        }
        if (inputType == null || outputType == null) {
            throw new IllegalArgumentException("input/output types are required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        requiredRoles = Set.copyOf(requiredRoles);   // defensive immutability
    }
}
