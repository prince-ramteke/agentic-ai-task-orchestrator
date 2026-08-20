package com.prince.agentic.account;

import com.prince.agentic.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the current authenticated principal ("who am I"). Requires authentication
 * (any role) via the deny-by-default policy. Demonstrates that {@link AuthenticatedUser}
 * is resolvable in controllers — the same identity future agent tools will authorize against.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Account", description = "Current authenticated user")
public class MeController {

    @GetMapping
    @Operation(summary = "Current authenticated user's identity and roles")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.userId(), user.email(), user.roles());
    }
}
