package com.prince.agentic.tool;

/**
 * Side-effect / risk classification of a tool, aligned with {@code docs/TOOL_SYSTEM.md} §4.
 *
 * <p>Security strictness increases with risk. M5 establishes the classification and exposes it as
 * metadata; the M8 guardrail system will use it to gate confirmation/enforcement.
 *
 * <ul>
 *   <li>{@code READ_ONLY} — no state change (e.g. {@code task.get}, {@code task.search}).</li>
 *   <li>{@code DETERMINISTIC} — pure computation, no I/O side effects (e.g. {@code math.calculate}).</li>
 *   <li>{@code SIDE_EFFECTING} — creates/updates state (e.g. {@code task.create}).</li>
 *   <li>{@code HIGH_RISK} — irreversible/destructive; mandatory confirmation in M8 (e.g. a future delete).</li>
 * </ul>
 */
public enum ToolRiskLevel {
    READ_ONLY,
    DETERMINISTIC,
    SIDE_EFFECTING,
    HIGH_RISK
}
