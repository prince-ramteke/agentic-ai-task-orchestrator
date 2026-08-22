package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentStatus;
import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.agent.AuditConfirmationExecuted;
import com.prince.agentic.agent.AuditExecutionEnd;
import com.prince.agentic.agent.AuditExecutionStart;
import com.prince.agentic.agent.AuditStepEvent;
import com.prince.agentic.agent.AuditToolEvent;
import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import com.prince.agentic.tool.ToolRiskLevel;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-PostgreSQL persistence for the audit writer/repositories (Testcontainers): round-trip,
 * idempotency (UNIQUE natural keys), owner-scoped filtering + pagination, and confirm-promotion.
 * Uses the transactional {@link AuditWriter} directly (its {@code REQUIRES_NEW} writes commit), so
 * unique uids per test keep the shared container clean.
 */
class AgentAuditPersistenceIT extends AbstractPostgresIntegrationTest {

    @Autowired private AuditWriter writer;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private AgentStepRepository steps;
    @Autowired private ToolExecutionRepository toolExecutions;
    @Autowired private UserRepository users;

    private static final Instant T = Instant.parse("2026-08-22T12:00:00Z");
    private static final AtomicLong SEQ = new AtomicLong();

    private long newUser() {
        String email = "audit-it-" + SEQ.incrementAndGet() + "@example.com";
        return users.save(new User(email, "$2a$10$abcdefghijklmnopqrstuv")).getId();
    }

    private String uid() {
        return UUID.randomUUID().toString();
    }

    @Test
    void fullLifecycle_roundTrips() {
        long owner = newUser();
        String exec = uid();
        writer.createExecution(new AuditExecutionStart(exec, owner, "conv-1", "req-1", T));
        writer.addStep(new AuditStepEvent(exec, 0, AgentStepKind.LLM_DECISION, AgentStepOutcome.OK,
                "task.search", "TOOL_CALL", T, T, 1L));
        writer.addStep(new AuditStepEvent(exec, 1, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK,
                "task.search", null, T, T, 5L));
        writer.addToolExecution(new AuditToolEvent(uid(), exec, 1, "task.search", ToolRiskLevel.READ_ONLY,
                AgentToolOutcome.SUCCESS, null, null, "hash", "found 2", T, T, 5L));
        writer.completeExecution(new AuditExecutionEnd(exec, AgentStatus.COMPLETED, null, "You have 2.",
                2, 1, T.plusSeconds(1), 1000L));

        AgentExecutionRecord e = executions.findByExecutionUid(exec).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(AuditExecutionStatus.COMPLETED);
        assertThat(e.getFinalResponseSummary()).isEqualTo("You have 2.");
        assertThat(steps.findByExecutionIdOrderBySequenceAsc(e.getId())).hasSize(2);
        assertThat(toolExecutions.findByExecutionIdOrderByStartedAtAsc(e.getId())).hasSize(1);
    }

    @Test
    void idempotent_createAndStepAndTool_doNotDuplicate() {
        long owner = newUser();
        String exec = uid();
        String teUid = uid();
        writer.createExecution(new AuditExecutionStart(exec, owner, null, null, T));
        writer.createExecution(new AuditExecutionStart(exec, owner, null, null, T)); // duplicate uid
        long execId = executions.findByExecutionUid(exec).orElseThrow().getId();

        writer.addStep(new AuditStepEvent(exec, 0, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK, "t", null, T, T, 1L));
        writer.addStep(new AuditStepEvent(exec, 0, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK, "t", null, T, T, 1L));
        writer.addToolExecution(new AuditToolEvent(teUid, exec, 0, "t", ToolRiskLevel.READ_ONLY,
                AgentToolOutcome.SUCCESS, null, null, null, null, T, T, 1L));
        writer.addToolExecution(new AuditToolEvent(teUid, exec, 0, "t", ToolRiskLevel.READ_ONLY,
                AgentToolOutcome.SUCCESS, null, null, null, null, T, T, 1L)); // duplicate uid

        assertThat(executions.findAll().stream().filter(x -> x.getExecutionUid().equals(exec)).count()).isEqualTo(1L);
        assertThat(steps.findByExecutionIdOrderBySequenceAsc(execId)).hasSize(1);
        assertThat(toolExecutions.findByExecutionIdOrderByStartedAtAsc(execId)).hasSize(1);
    }

    @Test
    void findOwnedFiltered_isOwnerScoped_andPaginated() {
        long ownerA = newUser();
        long ownerB = newUser();
        writer.createExecution(new AuditExecutionStart(uid(), ownerA, "cX", null, T));
        writer.createExecution(new AuditExecutionStart(uid(), ownerA, "cX", null, T.plusSeconds(10)));
        writer.createExecution(new AuditExecutionStart(uid(), ownerB, "cX", null, T));

        assertThat(executions.findAll(AgentExecutionSpecifications.filtered(ownerA, null, null, null, null, null),
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2L);
        assertThat(executions.findAll(AgentExecutionSpecifications.filtered(ownerB, null, null, null, null, null),
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1L);
        // conversation filter still owner-scoped: B's cX does not leak into A's results
        assertThat(executions.findAll(AgentExecutionSpecifications.filtered(ownerA, null, "cX", null, null, null),
                PageRequest.of(0, 1)).getContent()).hasSize(1);
        // a date-range filter (nullable Instant) must not break parameter typing
        assertThat(executions.findAll(AgentExecutionSpecifications.filtered(
                        ownerA, null, null, T.minusSeconds(1), T.plusSeconds(100), null),
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2L);
    }

    @Test
    void auditWriteTiming_isMeasured() {
        long owner = newUser();
        int runs = 50;
        long startNanos = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            String exec = uid();
            writer.createExecution(new AuditExecutionStart(exec, owner, "c", "r", T));
            writer.addStep(new AuditStepEvent(exec, 0, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK,
                    "task.search", null, T, T, 1L));
            writer.addToolExecution(new AuditToolEvent(uid(), exec, 0, "task.search",
                    ToolRiskLevel.READ_ONLY, AgentToolOutcome.SUCCESS, null, null, "h", "ok", T, T, 1L));
            writer.completeExecution(new AuditExecutionEnd(exec, AgentStatus.COMPLETED, null, "done",
                    1, 1, T, 1L));
        }
        double perRunMs = (System.nanoTime() - startNanos) / 1_000_000.0 / runs;
        // Not a strict SLA — just a measured figure recorded in the build output (spec §36, §15 M9).
        System.out.printf("MEASURED audit: %d full lifecycles (4 writes each), avg %.2f ms/run%n",
                runs, perRunMs);
        assertThat(perRunMs).isLessThan(500.0); // generous ceiling to catch pathological regressions only
    }

    @Test
    void confirmPromotion_appendsStepAndTool_andPromotesStatus() {
        long owner = newUser();
        String exec = uid();
        writer.createExecution(new AuditExecutionStart(exec, owner, "c", "r", T));
        writer.completeExecution(new AuditExecutionEnd(exec, AgentStatus.PENDING_CONFIRMATION,
                "CONFIRMATION_REQUIRED", null, 1, 0, T, 3L));

        writer.recordConfirmationExecution(new AuditConfirmationExecuted(exec, "conf-1", "task.create",
                ToolRiskLevel.SIDE_EFFECTING, "hash", true, null, "{\"id\":42}", T, T.plusSeconds(1)));

        AgentExecutionRecord e = executions.findByExecutionUid(exec).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(AuditExecutionStatus.COMPLETED);
        assertThat(e.getToolCalls()).isEqualTo(1);
        assertThat(steps.findByExecutionIdOrderBySequenceAsc(e.getId()))
                .anyMatch(s -> s.getStepType() == AgentStepKind.CONFIRMATION_APPROVED);
        assertThat(toolExecutions.findByExecutionIdOrderByStartedAtAsc(e.getId()))
                .anyMatch(t -> "conf-1".equals(t.getConfirmationId()));

        // Idempotent: a repeated confirm record does not double-append.
        writer.recordConfirmationExecution(new AuditConfirmationExecuted(exec, "conf-1", "task.create",
                ToolRiskLevel.SIDE_EFFECTING, "hash", true, null, "{\"id\":42}", T, T.plusSeconds(1)));
        assertThat(toolExecutions.findByExecutionIdOrderByStartedAtAsc(e.getId())).hasSize(1);
    }
}
