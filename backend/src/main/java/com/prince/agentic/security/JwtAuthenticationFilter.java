package com.prince.agentic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates each request from a {@code Bearer} JWT, statelessly.
 *
 * <p>On a valid token it builds an {@link AuthenticatedUser} principal (from verified claims,
 * never a DB lookup) and places it in the security context. On a missing/invalid/expired token
 * it simply leaves the context empty and continues — the chain then rejects protected requests
 * with 401 via the entry point. It never returns 500 for a bad token, and never logs token
 * contents.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                authenticate(token, request);
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired/malformed token: reject by leaving the context unauthenticated.
                SecurityContextHolder.clearContext();
                log.warn("Rejected bearer token: {}", ex.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Jws<Claims> jws = jwtService.parse(token);
        Claims claims = jws.getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        List<String> roles = readRoles(claims);

        AuthenticatedUser principal = new AuthenticatedUser(userId, email, Set.copyOf(roles));
        var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @SuppressWarnings("unchecked")
    private List<String> readRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
