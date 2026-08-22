package com.prince.agentic.audit.retention;

import com.prince.agentic.audit.AuditProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M10 retention scheduler behavior — cutoff, batching, cap, disabled, overlap, failure. */
class AuditRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final int RETENTION_DAYS = 30;
    private static final Instant EXPECTED_CUTOFF = NOW.minus(Duration.ofDays(RETENTION_DAYS));

    private AuditProperties audit;
    private MeterRegistry meters;
    private Clock clock;

    @BeforeEach
    void setUp() {
        audit = new AuditProperties(RETENTION_DAYS, 500, 500);
        meters = new SimpleMeterRegistry();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private AuditRetentionJob job(AuditRetentionProperties props, AuditRetentionRepository repo) {
        return new AuditRetentionJob(audit, props, repo, clock, meters);
    }

    @Test
    void disabled_returnsImmediately_noRepoCall_noMetric() {
        AuditRetentionRepository repo = mock(AuditRetentionRepository.class);
        AuditRetentionJob j = job(new AuditRetentionProperties(false, null, 100, 10), repo);

        j.runOnce();

        verify(repo, never()).deleteExpiredBatch(any(), anyInt());
        assertThat(meters.counter("retention.purge.started", "table", "agent_executions").count()).isZero();
    }

    @Test
    void loopsUntilZero_thenStops_countingDeletedRows() {
        AuditRetentionRepository repo = mock(AuditRetentionRepository.class);
        when(repo.deleteExpiredBatch(eq(EXPECTED_CUTOFF), eq(100)))
                .thenReturn(100).thenReturn(37).thenReturn(0);
        AuditRetentionJob j = job(new AuditRetentionProperties(true, null, 100, 10), repo);

        j.runOnce();

        InOrder ord = inOrder(repo);
        ord.verify(repo, times(3)).deleteExpiredBatch(EXPECTED_CUTOFF, 100);
        assertThat(meters.counter("retention.purge.started", "table", "agent_executions").count()).isEqualTo(1);
        assertThat(meters.counter("retention.purge.deleted", "table", "agent_executions").count()).isEqualTo(137);
        assertThat(meters.timer("retention.purge.duration", "table", "agent_executions").count()).isEqualTo(1);
    }

    @Test
    void maxBatchesCap_isEnforced() {
        AuditRetentionRepository repo = mock(AuditRetentionRepository.class);
        // Repo always says "more available" — the job MUST stop after maxBatches.
        when(repo.deleteExpiredBatch(any(), anyInt())).thenReturn(50);
        AuditRetentionJob j = job(new AuditRetentionProperties(true, null, 50, 4), repo);

        j.runOnce();

        verify(repo, times(4)).deleteExpiredBatch(EXPECTED_CUTOFF, 50);
        assertThat(meters.counter("retention.purge.deleted", "table", "agent_executions").count()).isEqualTo(200);
    }

    @Test
    void batchFailure_isBestEffort_shortCircuit_neverThrows() {
        AuditRetentionRepository repo = mock(AuditRetentionRepository.class);
        when(repo.deleteExpiredBatch(any(), anyInt()))
                .thenReturn(100)
                .thenThrow(new RuntimeException("db kaboom"));
        AuditRetentionJob j = job(new AuditRetentionProperties(true, null, 100, 10), repo);

        j.runOnce(); // must not throw

        verify(repo, times(2)).deleteExpiredBatch(EXPECTED_CUTOFF, 100);
        assertThat(meters.counter("retention.purge.deleted", "table", "agent_executions").count()).isEqualTo(100);
        assertThat(meters.counter("retention.purge.failure", "table", "agent_executions").count()).isEqualTo(1);
    }

    @Test
    void overlappingCall_isSkipped_viaTryLock() throws Exception {
        // Blocking repo: latch waits for the second thread to try runOnce().
        CountDownLatch inBatch = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        // Anonymous subclass rather than a Mockito mock so we can block the first call reliably.
        // Passing null JdbcTemplate is safe — the overridden method never touches the field.
        AuditRetentionRepository blocking = new AuditRetentionRepository(null) {
            @Override
            public int deleteExpiredBatch(Instant cutoff, int batchSize) {
                if (calls.incrementAndGet() == 1) {
                    inBatch.countDown();
                    try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                }
                return 0;
            }
        };
        AuditRetentionJob j = job(new AuditRetentionProperties(true, null, 100, 10), blocking);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(j::runOnce);
            assertThat(inBatch.await(2, TimeUnit.SECONDS)).isTrue();
            // Second call: lock is held by the first; must return immediately without invoking repo.
            j.runOnce();
            release.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        // Exactly one repo call reached the batch — the overlapping call was skipped.
        assertThat(calls.get()).isEqualTo(1);
    }
}
