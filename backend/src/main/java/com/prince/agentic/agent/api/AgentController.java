package com.prince.agentic.agent.api;

import com.prince.agentic.agent.AgentConfirmationOutcome;
import com.prince.agentic.agent.AgentConfirmationService;
import com.prince.agentic.agent.AgentConversationService;
import com.prince.agentic.agent.AgentResult;
import com.prince.agentic.agent.ConversationOutcome;
import com.prince.agentic.agent.api.dto.AgentConfirmResponse;
import com.prince.agentic.agent.api.dto.AgentExecuteRequest;
import com.prince.agentic.agent.api.dto.AgentExecuteResponse;
import com.prince.agentic.guardrail.confirmation.PendingConfirmation;
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
 * The agent endpoint (M6 + M7 + M8). Thin: it resolves the authenticated principal and delegates.
 * Identity comes only from the verified principal — never from the request body, {@code conversationId},
 * or a {@code confirmationId}. Authenticated (deny-by-default).
 *
 * <p>M8: {@code /execute} may now return {@code status=PENDING_CONFIRMATION} with a safe confirmation
 * view; the caller then confirms the exact stored action via {@code POST /confirmations/{id}} (no
 * argument body — mutation is structurally impossible) or cancels it via {@code DELETE}.
 */
@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "Agent", description = "Backend-controlled agent execution with guardrails, confirmation, and memory")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentConversationService conversationService;
    private final AgentConfirmationService confirmationService;

    public AgentController(AgentConversationService conversationService,
                           AgentConfirmationService confirmationService) {
        this.conversationService = conversationService;
        this.confirmationService = confirmationService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Run one bounded agent execution, optionally continuing a conversation",
            description = "Omit conversationId to start a new conversation; the response returns the "
                    + "server-minted id to continue it. status may be PENDING_CONFIRMATION (a "
                    + "side-effecting action awaits confirmation — see the confirmation* fields) or "
                    + "BLOCKED (a guardrail denied the action). memoryStatus is ACTIVE, or UNAVAILABLE "
                    + "when Redis memory could not be used.")
    public AgentExecuteResponse execute(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody AgentExecuteRequest request) {
        ConversationOutcome outcome =
                conversationService.execute(user, request.message(), request.conversationId());
        AgentResult result = outcome.result();
        PendingConfirmation pc = outcome.pendingConfirmation();
        return new AgentExecuteResponse(
                result.executionId(),
                result.status().name(),
                result.finalResponse(),
                result.iterations(),
                result.toolCalls(),
                result.durationMs(),
                result.failureCode(),
                outcome.conversationId(),
                outcome.memoryStatus().name(),
                pc == null ? null : pc.confirmationId(),
                pc == null ? null : pc.tool(),
                pc == null ? null : pc.riskLevel().name(),
                pc == null ? null : pc.summary(),
                pc == null ? null : pc.expiresAt().toString());
    }

    @PostMapping("/confirmations/{id}")
    @Operation(summary = "Confirm and execute a pending side-effecting action exactly once",
            description = "Executes the exact stored, fingerprint-bound action for this confirmation. "
                    + "Takes no arguments — the stored action is what runs. Single-use: a replay or a "
                    + "concurrent second confirm is rejected. Returns 404 (masked) for a missing/foreign "
                    + "id, 410 if expired, 409 if already used or tampered.")
    public AgentConfirmResponse confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable("id") String confirmationId) {
        AgentConfirmationOutcome outcome = confirmationService.confirm(user, confirmationId);
        return new AgentConfirmResponse(
                outcome.confirmationId(),
                outcome.tool(),
                outcome.success() ? "EXECUTED" : "FAILED",
                outcome.resultSummary(),
                outcome.errorCode());
    }

    @DeleteMapping("/confirmations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a pending confirmation",
            description = "Owner-scoped. Returns 404 (masked) for a missing, expired, or non-owned "
                    + "confirmation.")
    public void cancelConfirmation(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable("id") String confirmationId) {
        confirmationService.cancel(user, confirmationId);
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
