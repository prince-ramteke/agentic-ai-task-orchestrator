package com.prince.agentic.audit.retention;

import com.prince.agentic.audit.AuditProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Milestone 10 scheduled audit-retention enforcer (ADR-0031). Runs on the {@code audit.purge.cron}
 * schedule in UTC; also invokable directly via {@link #runOnce()} for integration tests.
 *
 * <p><b>Contract:</b>
 * <ul>
 *   <li>Deletes {@code agent_executions} rows with {@code started_at &lt; now - retentionDays}
 *       in bounded batches; children cascade via existing FKs.</li>
 *   <li>Never touches rows that are equal to or newer than the cutoff.</li>
 *   <li>At most {@code maxBatches} batches per invocation — a per-run ceiling of
 *       {@code maxBatches * batchSize} rows (default 100 * 500 = 50 000).</li>
 *   <li>Best-effort: any batch failure logs WARN, increments {@code retention.purge.failure},
 *       short-circuits the loop, and <b>never rethrows</b>.</li>
 *   <li>Single-node overlap-safe: {@link ReentrantLock#tryLock()} guarantees only one purge runs
 *       at a time within this JVM (distributed locking is out of scope per spec §12).</li>
 * </ul>
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
    /** The one table this job purges; used as the sole low-cardinality metric tag. */
    static final String TABLE = "agent_executions";

    private final AuditProperties audit;
    private final AuditRetentionProperties purge;
    private final AuditRetentionRepository repo;
    private final Clock clock;
    private final MeterRegistry meters;
    private final ReentrantLock lock = new ReentrantLock();

    public AuditRetentionJob(AuditProperties audit,
                             AuditRetentionProperties purge,
                             AuditRetentionRepository repo,
                             Clock clock,
                             MeterRegistry meters) {
        this.audit = audit;
        this.purge = purge;
        this.repo = repo;
        this.clock = clock;
        this.meters = meters;
    }

    /** Cron entry point. Delegates to {@link #runOnce()} so tests can invoke the same code path. */
    @Scheduled(cron = "${audit.purge.cron}", zone = "UTC")
    public void scheduled() {
        runOnce();
    }

    /** Execute one purge invocation. Safe to call directly. Never throws. */
    public void runOnce() {
        if (!purge.enabled()) {
            return;
        }
        if (!lock.tryLock()) {
            log.info("retention.purge.skipped_overlap table={}", TABLE);
            return;
        }
        Timer.Sample sample = Timer.start(meters);
        long totalDeleted = 0;
        int batches = 0;
        meters.counter("retention.purge.started", "table", TABLE).increment();
        try {
            Instant cutoff = clock.instant().minus(Duration.ofDays(audit.retentionDays()));
            for (int i = 0; i < purge.maxBatches(); i++) {
                int n;
                try {
                    n = repo.deleteExpiredBatch(cutoff, purge.batchSize());
                } catch (RuntimeException e) {
                    meters.counter("retention.purge.failure", "table", TABLE).increment();
                    log.warn("retention.purge.batch_failed table={} error={}",
                            TABLE, e.getClass().getSimpleName());
                    break;
                }
                if (n == 0) {
                    break;
                }
                totalDeleted += n;
                batches++;
                meters.counter("retention.purge.deleted", "table", TABLE).increment(n);
            }
        } finally {
            sample.stop(Timer.builder("retention.purge.duration").tag("table", TABLE).register(meters));
            lock.unlock();
            log.info("retention.purge.completed table={} batches={} deleted={}",
                    TABLE, batches, totalDeleted);
        }
    }
}
