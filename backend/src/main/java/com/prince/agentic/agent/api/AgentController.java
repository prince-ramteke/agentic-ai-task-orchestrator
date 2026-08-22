package com.prince.agentic.agent.api;

import com.prince.agentic.agent.AgentConversationService;
import com.prince.agentic.agent.AgentResult;
import com.prince.agentic.agent.ConversationOutcome;
import com.prince.agentic.agent.api.dto.AgentExecuteRequest;
import com.prince.agentic.agent.api.dto.AgentExecuteResponse;
import com.prince.agentic.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent endpoint (M6 + M7). Thin: it resolves the authenticated principal and delegates to
 * {@link AgentConversationService}, which loads bounded conversation memory, runs the Redis-free M6
 * orchestrator with that context, and appends the bounded turn. Identity comes only from the verified
 * principal — never from the request body or {@code conversationId}. Authenticated (deny-by-default).
 */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent", description = "Backend-controlled agent execution with conversation memory (M6+M7)")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentConversationService conversationService;

    public AgentController(AgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Run one bounded agent execution, optionally continuing a conversation",
            description = "Omit conversationId to start a new conversation; the response returns the "
                    + "server-minted id to continue it. memoryStatus is ACTIVE, or UNAVAILABLE when "
                    + "Redis memory could not be used.")
    public AgentExecuteResponse execute(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody AgentExecuteRequest request) {
        ConversationOutcome outcome =
                conversationService.execute(user, request.message(), request.conversationId());
        AgentResult result = outcome.result();
        return new AgentExecuteResponse(
                result.executionId(),
                result.status().name(),
                result.finalResponse(),
                result.iterations(),
                result.toolCalls(),
                result.durationMs(),
                result.failureCode(),
                outcome.conversationId(),
                outcome.memoryStatus().name());
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a conversation's memory",
            description = "Deletes the authenticated user's conversation. Returns 404 (existence-masked) "
                    + "for a missing, expired, or non-owned conversation.")
    public void deleteConversation(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable("id") String conversationId) {
        conversationService.delete(user, conversationId);
    }
}
