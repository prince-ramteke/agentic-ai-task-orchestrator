package com.prince.agentic.audit.retention;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Env-tunable retention purge bounds (M10, ADR-0031). Bound via {@code audit.purge.*}; overridable
 * via {@code AGENT_AUDIT_PURGE_ENABLED}, {@code AGENT_AUDIT_PURGE_CRON},
 * {@code AGENT_AUDIT_PURGE_BATCH_SIZE}, {@code AGENT_AUDIT_PURGE_MAX_BATCHES}. Zero/blank values
 * fall back to the documented defaults (mirrors the {@code AuditProperties} idiom).
 *
 * <p>The retention horizon itself lives on {@code AuditProperties.retentionDays} — a single source
 * of truth. This record only governs <b>how</b> the purge runs.
 */
@Validated
@ConfigurationProperties("audit.purge")
public record AuditRetentionProperties(
        boolean enabled,
        String cron,
        @Min(1) int batchSize,
        @Min(1) int maxBatches) {

    public AuditRetentionProperties {
        if (cron == null || cron.isBlank()) cron = "0 15 3 * * *";
        if (batchSize == 0) batchSize = 500;
        if (maxBatches == 0) maxBatches = 100;
    }
}
