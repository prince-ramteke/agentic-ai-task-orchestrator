package com.prince.agentic.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Reusable, server-side ownership/authorization checks.
 *
 * <p>This is the authorization primitive that future domain services and agent tools call
 * <em>before</em> acting on a resource. The rule: a non-admin user may only touch resources
 * they own; an admin may act per administrative permission. Ownership is always checked
 * against the authenticated principal's id — never a client- or model-supplied claim.
 *
 * <p>No domain resource exists yet (M2), so this operates on an owner id supplied by the
 * caller; M3+ services pass the persisted resource's {@code ownerId}. Throwing
 * {@link AccessDeniedException} yields a consistent 403 through the global error handling.
 */
@Service
public class AuthorizationService {

    /**
     * Allow the action only if the user owns the resource or is an admin; otherwise deny.
     *
     * @throws AccessDeniedException if the user is absent or is a non-owner, non-admin
     */
    public void requireOwnershipOrAdmin(AuthenticatedUser user, Long resourceOwnerId) {
        if (!canAccess(user, resourceOwnerId)) {
            throw new AccessDeniedException("You do not have permission to access this resource.");
        }
    }

    /** Non-throwing variant for conditional logic. */
    public boolean canAccess(AuthenticatedUser user, Long resourceOwnerId) {
        if (user == null) {
            return false;
        }
        return user.isAdmin() || Objects.equals(user.userId(), resourceOwnerId);
    }
}
