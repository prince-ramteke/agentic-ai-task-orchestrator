package com.prince.agentic.tool.exception;

import com.prince.agentic.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the stable status + machine code of each tool exception. */
class ToolExceptionTest {

    @Test
    void codes_and_statuses_are_stable() {
        assertThat(new ToolNotFoundException("task.x")).isInstanceOf(ApiException.class);
        assertThat(new ToolNotFoundException("task.x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new ToolNotFoundException("task.x").getCode()).isEqualTo("TOOL_NOT_FOUND");

        assertThat(new ToolInvalidInputException("bad").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new ToolInvalidInputException("bad").getCode()).isEqualTo("TOOL_INVALID_INPUT");

        assertThat(new ToolUnauthorizedException("x").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new ToolUnauthorizedException("x").getCode()).isEqualTo("TOOL_UNAUTHORIZED");

        assertThat(new ToolForbiddenException("x").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new ToolForbiddenException("x").getCode()).isEqualTo("TOOL_FORBIDDEN");

        assertThat(new ToolTimeoutException("x").getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(new ToolTimeoutException("x").getCode()).isEqualTo("TOOL_TIMEOUT");

        assertThat(new ToolExecutionFailedException("x", null).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(new ToolExecutionFailedException("x", null).getCode()).isEqualTo("TOOL_EXECUTION_FAILED");
    }

    @Test
    void execution_failed_keeps_cause() {
        Throwable cause = new IllegalStateException("root");
        assertThat(new ToolExecutionFailedException("boom", cause).getCause()).isSameAs(cause);
    }
}
