package com.prince.agentic.ai.llm.exception;

import org.springframework.http.HttpStatus;

/**
 * A provider/runtime failure that is neither a plain unavailability nor a timeout (e.g. a 5xx from
 * the provider, or a malformed transport response). Maps to {@code 502 BAD_GATEWAY} /
 * {@code LLM_PROVIDER_ERROR}.
 *
 * <p>The originating cause is attached for server-side logging by the global handler; it is never
 * returned to the client.
 */
public class LlmProviderException extends LlmException {

    public LlmProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "LLM_PROVIDER_ERROR", message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
