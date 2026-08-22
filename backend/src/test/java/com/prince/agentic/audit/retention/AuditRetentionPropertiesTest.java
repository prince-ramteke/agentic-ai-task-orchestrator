package com.prince.agentic.audit.retention;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Defaults + compact constructor normalization (M10, ADR-0031). */
class AuditRetentionPropertiesTest {

    @Test
    void defaults_fillZerosAndBlanks() {
        AuditRetentionProperties p = new AuditRetentionProperties(true, null, 0, 0);
        assertThat(p.enabled()).isTrue();
        assertThat(p.cron()).isEqualTo("0 15 3 * * *");
        assertThat(p.batchSize()).isEqualTo(500);
        assertThat(p.maxBatches()).isEqualTo(100);
    }

    @Test
    void blankCron_isNormalized() {
        AuditRetentionProperties p = new AuditRetentionProperties(false, "   ", 10, 5);
        assertThat(p.cron()).isEqualTo("0 15 3 * * *");
        assertThat(p.enabled()).isFalse();
        assertThat(p.batchSize()).isEqualTo(10);
        assertThat(p.maxBatches()).isEqualTo(5);
    }

    @Test
    void explicitValues_areRespected() {
        AuditRetentionProperties p = new AuditRetentionProperties(true, "0 0 4 * * *", 250, 40);
        assertThat(p.cron()).isEqualTo("0 0 4 * * *");
        assertThat(p.batchSize()).isEqualTo(250);
        assertThat(p.maxBatches()).isEqualTo(40);
    }
}
