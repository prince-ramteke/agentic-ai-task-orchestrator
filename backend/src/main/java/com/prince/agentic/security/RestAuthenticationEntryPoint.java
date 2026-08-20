package com.prince.agentic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles unauthenticated access to a protected endpoint → 401 in the standard JSON envelope.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final SecurityErrorResponder responder;

    public RestAuthenticationEntryPoint(SecurityErrorResponder responder) {
        this.responder = responder;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized request to {} {}", request.getMethod(), request.getRequestURI());
        responder.write(request, response, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required to access this resource.");
    }
}
