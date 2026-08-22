package com.prince.agentic.audit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Env-tunable audit bounds (spec §13, §4). Bound via {@code audit.*}; overridable via
 * {@code AGENT_AUDIT_RETENTION_DAYS}, {@code AGENT_AUDIT_FINAL_SUMMARY_MAX_CHARS},
 * {@code AGENT_AUDIT_RESULT_SUMMARY_MAX_CHARS}. Zero/unset → defaults (mirrors
 * {@code MemoryProperties}/{@code GuardrailProperties}). Summary caps are ≤ the 600-char DB columns.
 *
 * <p>{@code retentionDays} documents the intended retention horizon only — <b>no purge scheduler is
 * implemented in M9</b>; enforcement is deferred (spec §13).
 */
@Validated
@ConfigurationProperties("audit")
public record AuditProperties(
        @Min(1) int retentionDays,
        @Min(1) int finalSummaryMaxChars,
        @Min(1) int resultSummaryMaxChars) {

    public AuditProperties {
        if (retentionDays == 0) retentionDays = 90;
        if (finalSummaryMaxChars == 0) finalSummaryMaxChars = 500;
        if (resultSummaryMaxChars == 0) resultSummaryMaxChars = 500;
    }
}
