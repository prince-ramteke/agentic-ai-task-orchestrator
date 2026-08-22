package com.prince.agentic.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Persistence for {@link AgentExecutionRecord}. Owner-scoped filtered listing uses type-safe JPA
 * {@link org.springframework.data.jpa.domain.Specification}s ({@link AgentExecutionSpecifications}) via
 * {@link JpaSpecificationExecutor}: a null filter simply contributes no predicate, so no untyped-null
 * bind is ever sent (PostgreSQL rejects `CAST(null AS timestamp)`). Ownership is always applied in SQL.
 */
public interface AgentExecutionRepository
        extends JpaRepository<AgentExecutionRecord, Long>, JpaSpecificationExecutor<AgentExecutionRecord> {

    Optional<AgentExecutionRecord> findByExecutionUid(String executionUid);

    Optional<AgentExecutionRecord> findByExecutionUidAndOwnerId(String executionUid, Long ownerId);
}
