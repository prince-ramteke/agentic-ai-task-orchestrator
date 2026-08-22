package com.prince.agentic.audit.api;

import com.prince.agentic.audit.AgentAuditQueryService;
import com.prince.agentic.audit.AuditExecutionStatus;
import com.prince.agentic.audit.api.dto.AgentExecutionDetailResponse;
import com.prince.agentic.audit.api.dto.AgentExecutionSummaryResponse;
import com.prince.agentic.common.response.PageResponse;
import com.prince.agentic.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read-only agent execution history (M9). Owner-scoped and paginated; a foreign/missing execution id
 * returns a masked 404. Responses are sanitized DTOs — never internal class names, raw prompts,
 * arguments, LLM output, chain-of-thought, stack traces, or secrets. Audit is append-only via the
 * internal listener, so there are no write endpoints here. Authenticated (deny-by-default).
 */
@RestController
@RequestMapping("/api/v1/agent/executions")
@Tag(name = "Agent Audit", description = "Durable, owner-scoped agent execution history (M9)")
@SecurityRequirement(name = "bearerAuth")
public class AgentAuditController {

    private final AgentAuditQueryService query;

    public AgentAuditController(AgentAuditQueryService query) {
        this.query = query;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's agent executions (paginated, filterable)",
            description = "Filters: status, conversationId, from/to (ISO-8601 instants), toolName. "
                    + "Sortable by startedAt/completedAt/status; default startedAt DESC. Owner-scoped.")
    public PageResponse<AgentExecutionSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) AuditExecutionStatus status,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return query.list(user, status, conversationId, from, to, toolName, page, size, sort);
    }

    @GetMapping("/{executionId}")
    @Operation(summary = "Get one of the user's executions with its ordered steps and tool executions",
            description = "Returns 404 (existence-masked) for a missing or non-owned execution.")
    public AgentExecutionDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable("executionId") String executionId) {
        return query.get(user, executionId);
    }
}
