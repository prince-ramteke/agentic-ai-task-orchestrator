package com.prince.agentic.security;

import java.util.Set;

/**
 * The authenticated principal, decoupled from the JWT mechanism.
 *
 * <p>This is the identity that business services and — crucially — future agent tools
 * receive to make authorization decisions. It is deliberately small and transport-agnostic:
 * the security layer builds it from a verified token; nothing downstream ever parses raw
 * claims. The roles are the granted authorities (e.g. {@code ROLE_USER}).
 *
 * @param userId the authenticated user's id (never client-supplied — taken from the verified token)
 * @param email  the authenticated user's email
 * @param roles  the granted authorities
 */
public record AuthenticatedUser(Long userId, String email, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains(RoleNames.ROLE_ADMIN);
    }

    /** Accepts either {@code ADMIN} or {@code ROLE_ADMIN}. */
    public boolean hasRole(String role) {
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.contains(authority);
    }
}
