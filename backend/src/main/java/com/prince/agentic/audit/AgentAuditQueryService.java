package com.prince.agentic.audit;

import com.prince.agentic.audit.api.dto.AgentExecutionDetailResponse;
import com.prince.agentic.audit.api.dto.AgentExecutionSummaryResponse;
import com.prince.agentic.audit.api.dto.AgentStepResponse;
import com.prince.agentic.audit.api.dto.ToolExecutionResponse;
import com.prince.agentic.audit.exception.ExecutionNotFoundException;
import com.prince.agentic.common.query.SortWhitelist;
import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Owner-scoped read model over durable audit (spec §11, §12). Every query filters by the authenticated
 * user's id <b>in SQL</b> (never load-all-then-filter); a foreign or missing execution id is masked as
 * 404. Maps entities to safe DTOs — entities never cross the API boundary, and no raw prompt/argument/
 * output/chain-of-thought/secret is ever exposed.
 */
@Service
@Transactional(readOnly = true)
public class AgentAuditQueryService {

    // Only stable, non-sensitive columns are sortable; default newest-first.
    private static final SortWhitelist SORT = new SortWhitelist(
            Set.of("startedAt", "completedAt", "status"), "startedAt", Sort.Direction.DESC);

    private final AgentExecutionRepository executions;
    private final AgentStepRepository steps;
    private final ToolExecutionRepository toolExecutions;

    public AgentAuditQueryService(AgentExecutionRepository executions, AgentStepRepository steps,
                                  ToolExecutionRepository toolExecutions) {
        this.executions = executions;
        this.steps = steps;
        this.toolExecutions = toolExecutions;
    }

    public PageResponse<AgentExecutionSummaryResponse> list(
            AuthenticatedUser user, AuditExecutionStatus status, String conversationId,
            Instant from, Instant to, String toolName, Integer page, Integer size, String sort) {
        Pageable pageable = SORT.toPageable(page, size, sort);
        return PageResponse.from(
                executions.findAll(
                        AgentExecutionSpecifications.filtered(user.userId(), status, conversationId, from, to, toolName),
                        pageable),
                AgentAuditQueryService::toSummary);
    }

    public AgentExecutionDetailResponse get(AuthenticatedUser user, String executionId) {
        AgentExecutionRecord e = executions.findByExecutionUidAndOwnerId(executionId, user.userId())
                .orElseThrow(ExecutionNotFoundException::new);
        List<AgentStepResponse> stepViews = steps.findByExecutionIdOrderBySequenceAsc(e.getId()).stream()
                .map(AgentAuditQueryService::toStep)
                .toList();
        List<ToolExecutionResponse> toolViews = toolExecutions.findByExecutionIdOrderByStartedAtAsc(e.getId())
                .stream().map(AgentAuditQueryService::toTool).toList();
        return new AgentExecutionDetailResponse(
                e.getExecutionUid(), e.getStatus().name(), e.getConversationId(), e.getStartedAt(),
                e.getCompletedAt(), e.getDurationMs(), e.getIterations(), e.getToolCalls(),
                e.getFailureCode(), e.getFinalResponseSummary(), stepViews, toolViews);
    }

    private static AgentExecutionSummaryResponse toSummary(AgentExecutionRecord e) {
        return new AgentExecutionSummaryResponse(
                e.getExecutionUid(), e.getStatus().name(), e.getConversationId(), e.getStartedAt(),
                e.getCompletedAt(), e.getDurationMs(), e.getIterations(), e.getToolCalls(), e.getFailureCode());
    }

    private static AgentStepResponse toStep(AgentStepRecord s) {
        return new AgentStepResponse(s.getSequence(), s.getStepType().name(), s.getStatus().name(),
                s.getToolName(), s.getDetailCode(), s.getDurationMs());
    }

    private static ToolExecutionResponse toTool(ToolExecutionRecord t) {
        return new ToolExecutionResponse(t.getToolName(), t.getRiskLevel().name(), t.getOutcome().name(),
                t.getErrorCode(), t.getConfirmationId(), t.getArgumentsHash(), t.getResultSummary(),
                t.getDurationMs());
    }
}
