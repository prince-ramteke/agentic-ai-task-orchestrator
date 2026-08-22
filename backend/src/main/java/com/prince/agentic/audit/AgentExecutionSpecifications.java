package com.prince.agentic.audit;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe, owner-scoped filters for {@link AgentExecutionRecord} (spec §11, §12, §23). Each optional
 * filter contributes a predicate only when present, so a null filter is never bound — avoiding the
 * untyped-null problem entirely. Ownership is always enforced. No arbitrary query language.
 */
public final class AgentExecutionSpecifications {

    private AgentExecutionSpecifications() {
    }

    public static Specification<AgentExecutionRecord> filtered(
            long ownerId, AuditExecutionStatus status, String conversationId,
            Instant from, Instant to, String toolName) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (conversationId != null) {
                predicates.add(cb.equal(root.get("conversationId"), conversationId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            if (toolName != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ToolExecutionRecord> te = sub.from(ToolExecutionRecord.class);
                sub.select(cb.literal(1L)).where(
                        cb.equal(te.get("executionId"), root.get("id")),
                        cb.equal(te.get("toolName"), toolName));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
