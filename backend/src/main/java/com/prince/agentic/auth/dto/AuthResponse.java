package com.prince.agentic.auth.dto;

/**
 * Successful login response. Carries only the access token and its metadata — never the
 * password, hash, or any internal authentication detail.
 *
 * @param accessToken the signed JWT
 * @param tokenType   always {@code "Bearer"}
 * @param expiresIn   token lifetime in seconds
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
