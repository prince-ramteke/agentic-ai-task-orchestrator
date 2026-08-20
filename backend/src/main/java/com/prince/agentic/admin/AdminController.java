package com.prince.agentic.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only technical endpoint. It exists to demonstrate and regression-test RBAC: a
 * {@code ROLE_ADMIN} caller gets 200, a {@code ROLE_USER} caller gets 403, and an
 * unauthenticated caller gets 401. It is the template for future {@code /api/v1/admin/**}
 * routes (user management, execution/audit inspection) and carries no business data.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative endpoints (ROLE_ADMIN only)")
public class AdminController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin-only liveness probe (RBAC demonstration)")
    public AdminPingResponse ping() {
        return new AdminPingResponse("ok", "admin");
    }
}
