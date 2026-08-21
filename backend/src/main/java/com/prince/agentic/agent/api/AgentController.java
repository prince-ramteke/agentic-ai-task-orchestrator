package com.prince.agentic.agent.api;

import com.prince.agentic.agent.AgentOrchestrator;
import com.prince.agentic.agent.AgentResult;
import com.prince.agentic.agent.api.dto.AgentExecuteRequest;
import com.prince.agentic.agent.api.dto.AgentExecuteResponse;
import com.prince.agentic.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent endpoint (M6). Thin: it resolves the authenticated principal and delegates to
 * {@link AgentOrchestrator}, which owns the bounded decision/tool-execution loop. Identity comes
 * only from the verified principal — never from the request body. Authenticated (deny-by-default).
 */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent", description = "Backend-controlled agent execution (M6)")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/execute")
    @Operation(summary = "Run one bounded agent execution over registered tools")
    public AgentExecuteResponse execute(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody AgentExecuteRequest request) {
        AgentResult result = orchestrator.run(user, request.message());
        return new AgentExecuteResponse(
                result.executionId(),
                result.status().name(),
                result.finalResponse(),
                result.iterations(),
                result.toolCalls(),
                result.durationMs(),
                result.failureCode());
    }
}
