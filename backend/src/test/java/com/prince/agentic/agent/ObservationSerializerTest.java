package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.tool.ToolError;
import com.prince.agentic.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ObservationSerializerTest {

    private final AgentProperties props = new AgentProperties(8, 10, 60, 2, 40, 3);
    private final ObservationSerializer ser = new ObservationSerializer(new ObjectMapper(), props);

    @Test
    void success_serializes_and_truncates_to_maxChars() {
        String big = "x".repeat(500);
        AgentObservation obs = ser.toObservation(ToolResult.ok("task.get", big, 5));
        assertThat(obs.success()).isTrue();
        assertThat(obs.tool()).isEqualTo("task.get");
        assertThat(obs.resultSummary().length()).isLessThanOrEqualTo(40);
        assertThat(obs.errorCode()).isNull();
    }

    @Test
    void failure_carries_safe_code_and_message() {
        AgentObservation obs = ser.toObservation(
                ToolResult.failure("task.get", new ToolError("NOT_FOUND", "not found"), 3));
        assertThat(obs.success()).isFalse();
        assertThat(obs.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(obs.resultSummary()).isEqualTo("not found");
    }

    @Test
    void arrays_are_capped_to_maxArrayItems() {
        List<Integer> many = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        AgentObservation obs = ser.toObservation(ToolResult.ok("task.search", many, 4));
        // maxArrayItems=3 → serialized summary should not contain the 8th element
        assertThat(obs.resultSummary()).doesNotContain("8");
    }
}
