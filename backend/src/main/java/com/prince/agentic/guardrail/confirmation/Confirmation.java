package com.prince.agentic.guardrail.confirmation;

import com.prince.agentic.tool.ToolRiskLevel;

import java.util.Map;

/**
 * The stored, backend-authoritative pending confirmation (spec §6.2). Persisted as one
 * application-owned JSON blob under {@code guard:confirmation:{id}} in Redis, with a TTL. Holds the
 * <em>original validated action</em> plus the integrity fingerprint and ownership, so a confirm
 * executes exactly what was approved — never a client-supplied payload.
 *
 * <p>Times are epoch-millis longs to keep the blob serialization-module-independent. {@code fingerprint}
 * is recomputed and verified on confirm to detect any tampering of the bound fields.
 */
public record Confirmation(
        String id,
        long ownerUserId,
        String conversationId,
        String toolName,
        Map<String, Object> arguments,
        ToolRiskLevel riskLevel,
        String fingerprint,
        long createdAtEpochMs,
        long expiresAtEpochMs) {

    public Confirmation {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
