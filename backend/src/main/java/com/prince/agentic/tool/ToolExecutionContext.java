package com.prince.agentic.tool;

import com.prince.agentic.security.AuthenticatedUser;

import java.util.Map;
import java.util.UUID;

/**
 * The controlled execution identity + correlation for a single tool invocation.
 *
 * <p><b>Security invariant:</b> the context is constructed by the backend from the authenticated
 * {@link AuthenticatedUser} — never from tool arguments. The model may propose a tool name and
 * arguments, but it can never make a {@code userId} or {@code role} into security identity: tool
 * inputs carry no identity field, and identity here comes only from the verified principal.
 *
 * @param principal   the authenticated principal (source of userId/email/roles)
 * @param requestId   correlation id for the originating request
 * @param executionId id for this tool execution (stable within an agent run when supplied by M6)
 * @param metadata    optional, backend-controlled extra context (never sensitive data)
 */
public record ToolExecutionContext(
        AuthenticatedUser principal,
        String requestId,
        String executionId,
        Map<String, Object> metadata) {

    /**
     * Backend-controlled construction from the authenticated principal, generating fresh ids.
     * Use this (or an M6 variant that threads a stable executionId) — never build a context from
     * client/model-supplied identity.
     */
    public static ToolExecutionContext forPrincipal(AuthenticatedUser principal) {
        return new ToolExecutionContext(
                principal,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                Map.of());
    }
}
