package com.prince.agentic.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Minimal technical endpoint that confirms the application is serving requests and
 * reports its identity/profile. It is intentionally the only application endpoint in
 * Milestone 1 — it establishes the {@code /api/v1} path convention and the response-DTO
 * pattern that domain endpoints will follow, without introducing any fake business API.
 *
 * <p>Operational health checks (dependency/liveness/readiness) are served by Spring
 * Boot Actuator at {@code /actuator/health}; this endpoint is a simple app-level probe.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Technical liveness and application info")
public class HealthController {

    private final String applicationName;
    private final String applicationVersion;
    private final Environment environment;

    public HealthController(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.version}") String applicationVersion,
            Environment environment) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        this.environment = environment;
    }

    @GetMapping
    @Operation(summary = "Application liveness and info")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName,
                applicationVersion,
                List.of(environment.getActiveProfiles()),
                Instant.now());
    }
}
