package com.prince.agentic.health;

import java.time.Instant;
import java.util.List;

/**
 * Lightweight application liveness/info payload for {@code GET /api/v1/health}.
 *
 * <p>All values are real: status is fixed {@code "UP"} (the request reaching the
 * controller proves the web layer is serving), name/version/profiles come from the
 * running configuration, and {@code timestamp} is generated per request.
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        List<String> activeProfiles,
        Instant timestamp) {
}
