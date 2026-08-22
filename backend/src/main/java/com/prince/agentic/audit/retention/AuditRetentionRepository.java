package com.prince.agentic.audit.retention;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Parent-first batched purge of {@code agent_executions} (M10, ADR-0031). Relies on the existing
 * {@code ON DELETE CASCADE} FKs from {@code agent_steps.execution_id} and
 * {@code tool_executions.execution_id} (see {@code V5__create_agent_audit.sql}) so children are
 * removed transactionally by the database — no join, no separate child pass.
 *
 * <p>Uses a subquery with {@code LIMIT} rather than a plain DELETE-LIMIT because standard SQL and
 * H2's PostgreSQL-compat mode both accept the subquery form (PostgreSQL does not support
 * {@code DELETE ... LIMIT} directly). Each call is its own short {@link Transactional} unit so a
 * crash mid-loop leaves prior batches durably committed.
 */
@Repository
public class AuditRetentionRepository {

    private final JdbcTemplate jdbc;

    public AuditRetentionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Delete up to {@code batchSize} executions whose {@code started_at} is <b>strictly older</b>
     * than the supplied cutoff. Fresh rows (started_at &gt;= cutoff) are never touched.
     *
     * @return the number of parent rows actually deleted (children cascade)
     */
    @Transactional
    public int deleteExpiredBatch(Instant cutoff, int batchSize) {
        return jdbc.update("""
                DELETE FROM agent_executions
                 WHERE id IN (
                   SELECT id FROM agent_executions
                    WHERE started_at < ?
                    ORDER BY started_at
                    LIMIT ?
                 )
                """, Timestamp.from(cutoff), batchSize);
    }
}
