package com.prince.agentic.account;

import java.util.Set;

/**
 * The current authenticated user's identity, as resolved from the verified token.
 */
public record CurrentUserResponse(Long userId, String email, Set<String> roles) {
}
