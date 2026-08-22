package com.prince.agentic.audit.retention;

import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-PostgreSQL end-to-end proof of the M10 retention purge (ADR-0031):
 * <ul>
 *   <li>rows older than the retention cutoff are deleted;</li>
 *   <li>fresh rows survive untouched;</li>
 *   <li>{@code agent_steps} and {@code tool_executions} cascade with their parent (existing FK);</li>
 *   <li>{@code retention.purge.deleted} increments by the exact count;</li>
 *   <li>a second invocation is a no-op.</li>
 * </ul>
 *
 * <p>Seeds directly via {@link JdbcTemplate} so the test speaks the same language as the purge SQL
 * and avoids depending on package-private writer internals. The scheduled cron path is disabled in
 * the {@code it} profile (see {@code application-it.yml}); this test calls
 * {@link AuditRetentionJob#runOnce()} directly for determinism.
 */
class AuditRetentionIT extends AbstractPostgresIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private AuditRetentionJob job;
    @Autowired private MeterRegistry meters;
    @Autowired private JdbcTemplate jdbc;

    private static final AtomicLong SEQ = new AtomicLong();

    private long newUser() {
        String email = "retention-it-" + SEQ.incrementAndGet() + "@example.com";
        return users.save(new User(email, "$2a$10$abcdefghijklmnopqrstuv")).getId();
    }

    private String uid() { return UUID.randomUUID().toString(); }

    /**
     * Seed one {@code agent_executions} row with one child {@code agent_steps} and one child
     * {@code tool_executions}, all timestamped at the given instant. Uses raw JDBC so the test
     * makes no assumption about the writer's package visibility or hashing behaviour.
     */
    private String seedExecution(long owner, Instant startedAt) {
        String exec = uid();
        Timestamp ts = Timestamp.from(startedAt);

        jdbc.update("""
                INSERT INTO agent_executions
                    (execution_uid, owner_id, conversation_id, request_id, status,
                     iterations, tool_calls, started_at, created_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 1, 1, ?, ?)
                """, exec, owner, "conv", "req", ts, ts);

        Long execId = jdbc.queryForObject(
                "SELECT id FROM agent_executions WHERE execution_uid = ?", Long.class, exec);
        assertThat(execId).isNotNull();

        jdbc.update("""
                INSERT INTO agent_steps
                    (execution_id, sequence, step_type, status, tool_name, started_at, completed_at, duration_ms)
                VALUES (?, 0, 'TOOL_CALL', 'OK', 'task.search', ?, ?, 1)
                """, execId, ts, ts);
        Long stepId = jdbc.queryForObject(
                "SELECT id FROM agent_steps WHERE execution_id = ? AND sequence = 0",
                Long.class, execId);

        jdbc.update("""
                INSERT INTO tool_executions
                    (tool_execution_uid, step_id, execution_id, owner_id, tool_name, risk_level,
                     outcome, started_at, completed_at, duration_ms)
                VALUES (?, ?, ?, ?, 'task.search', 'READ_ONLY', 'SUCCESS', ?, ?, 1)
                """, uid(), stepId, execId, owner, ts, ts);
        return exec;
    }

    private long parentCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM agent_executions", Long.class);
        return n == null ? 0L : n;
    }

    private long stepCount(long execId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM agent_steps WHERE execution_id = ?",
                Long.class, execId);
        return n == null ? 0L : n;
    }

    private long toolExecCount(long execId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM tool_executions WHERE execution_id = ?",
                Long.class, execId);
        return n == null ? 0L : n;
    }

    @BeforeEach
    void isolate() {
        // Isolation across the shared Testcontainers Postgres.
        jdbc.update("DELETE FROM tool_executions");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_executions");
    }

    @Test
    void deletes_expired_preserves_fresh_cascadesChildren() {
        long owner = newUser();
        Instant now = Instant.now();
        Instant old = now.minusSeconds(100L * 24 * 3600);   // 100 days ago → past 90-day cutoff
        Instant fresh = now.minusSeconds(5L * 24 * 3600);   // 5 days ago → survives

        seedExecution(owner, old);
        seedExecution(owner, old);
        seedExecution(owner, old);
        String f1 = seedExecution(owner, fresh);
        seedExecution(owner, fresh);

        assertThat(parentCount()).isEqualTo(5);

        double before = meters.counter("retention.purge.deleted", "table", "agent_executions").count();

        job.runOnce();

        assertThat(parentCount()).isEqualTo(2);

        Long freshId = jdbc.queryForObject(
                "SELECT id FROM agent_executions WHERE execution_uid = ?", Long.class, f1);
        assertThat(freshId).isNotNull();
        assertThat(stepCount(freshId)).isEqualTo(1);   // cascade did NOT touch surviving parent's children
        assertThat(toolExecCount(freshId)).isEqualTo(1);

        // Purged parents' children are gone from the whole table.
        Long orphanSteps = jdbc.queryForObject("SELECT COUNT(*) FROM agent_steps", Long.class);
        Long orphanTools = jdbc.queryForObject("SELECT COUNT(*) FROM tool_executions", Long.class);
        assertThat(orphanSteps).isEqualTo(2L);   // only the 2 fresh parents' steps remain
        assertThat(orphanTools).isEqualTo(2L);

        double after = meters.counter("retention.purge.deleted", "table", "agent_executions").count();
        assertThat(after - before).isEqualTo(3.0);
    }

    @Test
    void secondInvocation_isNoOp() {
        long owner = newUser();
        Instant old = Instant.now().minusSeconds(100L * 24 * 3600);
        seedExecution(owner, old);

        job.runOnce();
        assertThat(parentCount()).isZero();

        double before = meters.counter("retention.purge.deleted", "table", "agent_executions").count();
        job.runOnce();
        double after = meters.counter("retention.purge.deleted", "table", "agent_executions").count();
        assertThat(after).isEqualTo(before);
    }
}
