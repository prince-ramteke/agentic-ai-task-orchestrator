package com.prince.agentic.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Persistence for {@link AgentStepRecord}; steps are read ordered by their monotonic sequence. */
public interface AgentStepRepository extends JpaRepository<AgentStepRecord, Long> {

    List<AgentStepRecord> findByExecutionIdOrderBySequenceAsc(Long executionId);

    boolean existsByExecutionIdAndSequence(Long executionId, int sequence);

    /** Highest sequence used by an execution, or -1 when it has no steps (so the next seq is 0). */
    @Query("SELECT COALESCE(MAX(s.sequence), -1) FROM AgentStepRecord s WHERE s.executionId = :executionId")
    int maxSequence(@Param("executionId") Long executionId);
}
