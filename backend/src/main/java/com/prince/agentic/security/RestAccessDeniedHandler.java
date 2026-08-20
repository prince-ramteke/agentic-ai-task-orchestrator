package com.prince.agentic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles authenticated-but-unauthorized access at the filter-chain level → 403 in the standard
 * JSON envelope. Method-level denials ({@code @PreAuthorize}) render the same envelope via the
 * global exception handler.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorResponder responder;

    public RestAccessDeniedHandler(SecurityErrorResponder responder) {
        this.responder = responder;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Forbidden request to {} {}", request.getMethod(), request.getRequestURI());
        responder.write(request, response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have permission to access this resource.");
    }
}
