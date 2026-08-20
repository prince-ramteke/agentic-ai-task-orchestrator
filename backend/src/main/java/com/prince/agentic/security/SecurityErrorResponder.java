package com.prince.agentic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Writes the standard {@link ApiError} JSON envelope directly to the response for security
 * failures that occur inside the filter chain (before the DispatcherServlet), so Spring
 * Security's default HTML/plain responses never leak into the API. Mirrors the shape and
 * per-response {@code traceId} used by the global exception handler.
 */
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String message) throws IOException {
        String traceId = UUID.randomUUID().toString();
        ApiError body = ApiError.of(status.value(), code, message, request.getRequestURI(), traceId);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
