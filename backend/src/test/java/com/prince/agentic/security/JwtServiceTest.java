package com.prince.agentic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for token issuance and verification. No Spring context — the service is
 * exercised directly with deterministic secrets.
 */
class JwtServiceTest {

    private static final String SECRET_A = "unit-test-secret-key-aaaaaaaaaa-0123456789";
    private static final String SECRET_B = "unit-test-secret-key-bbbbbbbbbb-0123456789";
    private static final String ISSUER = "agentic-ai-task-orchestrator";

    private final JwtService jwtService = new JwtService(SECRET_A, 3600, ISSUER);

    @Test
    void issueThenParse_roundTrips_claims() {
        String token = jwtService.issueToken(42L, "user@example.com", List.of("ROLE_USER"));

        Jws<Claims> parsed = jwtService.parse(token);
        Claims claims = parsed.getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiredToken_isRejected() {
        JwtService expiringService = new JwtService(SECRET_A, -3600, ISSUER); // already expired
        String token = expiringService.issueToken(1L, "user@example.com", List.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> jwtService.parse("this.is.not-a-valid-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        JwtService other = new JwtService(SECRET_B, 3600, ISSUER);
        String forged = other.issueToken(1L, "user@example.com", List.of("ROLE_ADMIN"));

        assertThatThrownBy(() -> jwtService.parse(forged)).isInstanceOf(JwtException.class);
    }
}
