package com.prince.agentic.memory.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Redis-backed conversation memory could not be reached (spec §3). Thrown at the fail-closed points:
 * loading or deleting an existing conversation, where proceeding without memory would be unsafe or
 * misleading. New conversations degrade to stateless execution instead of surfacing this.
 */
public class MemoryUnavailableException extends ApiException {

    public MemoryUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MEMORY_UNAVAILABLE",
                "Conversation memory is temporarily unavailable.");
        initCause(cause);
    }
}
