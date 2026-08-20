package com.prince.agentic.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the reusable ownership-authorization foundation that future services and
 * agent tools will call. Exercised with fabricated principals — no domain resource needed.
 */
class AuthorizationServiceTest {

    private final AuthorizationService authorizationService = new AuthorizationService();

    private static AuthenticatedUser user(long id, String... roles) {
        return new AuthenticatedUser(id, "u" + id + "@example.com", Set.of(roles));
    }

    @Test
    void owner_isAllowed() {
        AuthenticatedUser owner = user(7L, RoleNames.ROLE_USER);
        assertThatCode(() -> authorizationService.requireOwnershipOrAdmin(owner, 7L))
                .doesNotThrowAnyException();
        assertThat(authorizationService.canAccess(owner, 7L)).isTrue();
    }

    @Test
    void admin_canAccessAnyResource() {
        AuthenticatedUser admin = user(1L, RoleNames.ROLE_ADMIN);
        assertThatCode(() -> authorizationService.requireOwnershipOrAdmin(admin, 999L))
                .doesNotThrowAnyException();
        assertThat(authorizationService.canAccess(admin, 999L)).isTrue();
    }

    @Test
    void nonOwnerNonAdmin_isDenied() {
        AuthenticatedUser other = user(2L, RoleNames.ROLE_USER);
        assertThatThrownBy(() -> authorizationService.requireOwnershipOrAdmin(other, 7L))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(authorizationService.canAccess(other, 7L)).isFalse();
    }

    @Test
    void nullUser_isDenied() {
        assertThatThrownBy(() -> authorizationService.requireOwnershipOrAdmin(null, 7L))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(authorizationService.canAccess(null, 7L)).isFalse();
    }
}
