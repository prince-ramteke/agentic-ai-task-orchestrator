package com.prince.agentic.guardrail.confirmation;

import com.prince.agentic.security.AuthenticatedUser;

/**
 * Backend-authoritative confirmation lifecycle (spec §6). Storage-agnostic interface so the agent
 * layer depends on the contract, not on Redis.
 */
public interface ConfirmationService {

    /** Store the exact action, fingerprint-bound and TTL'd, and return a safe view for the client. */
    PendingConfirmation create(AuthenticatedUser principal, String conversationId, PendingAction action);

    /**
     * Atomically consume (single-use) and return the stored action, after verifying ownership,
     * expiry, and fingerprint integrity. Throws a guardrail exception otherwise; never executes.
     */
    ConfirmedAction confirm(AuthenticatedUser principal, String confirmationId);

    /** Owner-scoped cancellation of a pending confirmation. */
    void cancel(AuthenticatedUser principal, String confirmationId);
}
