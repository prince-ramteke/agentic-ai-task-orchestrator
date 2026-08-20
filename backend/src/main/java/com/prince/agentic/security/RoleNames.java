package com.prince.agentic.security;

/**
 * Canonical role/authority names. The stored role name equals the Spring Security
 * authority (prefixed {@code ROLE_}); {@code hasRole('ADMIN')} checks authority {@code ROLE_ADMIN}.
 */
public final class RoleNames {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private RoleNames() {
    }
}
