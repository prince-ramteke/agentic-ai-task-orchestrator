package com.prince.agentic.auth.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Safe representation of a user, returned by registration. Never includes the password hash.
 */
public record UserResponse(Long id, String email, Set<String> roles, Instant createdAt) {
}
