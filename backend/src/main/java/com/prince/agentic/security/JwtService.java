package com.prince.agentic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies stateless HS256 access tokens.
 *
 * <p>The token carries only what authorization needs: {@code sub} (user id), {@code email},
 * and {@code roles}. No password, hash, or other sensitive data is ever placed in a token.
 * Verification checks signature, issuer, and expiration; a bad token throws and is rejected.
 * The signing secret comes from the environment and is never logged.
 *
 * @see io.jsonwebtoken.security.WeakKeyException thrown at construction if the secret is
 *      shorter than 256 bits (HS256 requirement) — a fail-fast guard against weak secrets.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds,
            @Value("${security.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    /** Mint a signed token for an authenticated user. */
    public String issueToken(Long userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Parse and fully verify a token (signature, issuer, expiration).
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an
     *         invalid signature/issuer
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
