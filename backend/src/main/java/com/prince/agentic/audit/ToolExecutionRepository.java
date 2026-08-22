package com.prince.agentic.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link ToolExecutionRecord}; read ordered by start time for an execution. */
public interface ToolExecutionRepository extends JpaRepository<ToolExecutionRecord, Long> {

    List<ToolExecutionRecord> findByExecutionIdOrderByStartedAtAsc(Long executionId);

    boolean existsByToolExecutionUid(String toolExecutionUid);

    /** Guards confirm-path idempotency: a confirmation executes at most once (M8 single-use). */
    boolean existsByExecutionIdAndConfirmationId(Long executionId, String confirmationId);
}
