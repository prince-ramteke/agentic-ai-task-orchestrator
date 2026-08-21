package com.prince.agentic.agent;

import com.prince.agentic.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionTest {

    private final AuthenticatedUser user = new AuthenticatedUser(1L, "a@b.com", Set.of("ROLE_USER"));

    @Test
    void deadline_isStartPlusTimeout_computedOnce() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AgentExecution ex = new AgentExecution("exec-1", user, "req-1", clock,
                new AgentProperties(8, 10, 30, 2, 2000, 20));
        assertThat(ex.deadline()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    void counters_increment() {
        AgentExecution ex = newExec();
        ex.nextIteration(); ex.nextIteration();
        ex.recordToolCall();
        assertThat(ex.iteration()).isEqualTo(2);
        assertThat(ex.toolCallsUsed()).isEqualTo(1);
    }

    private AgentExecution newExec() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC);
        return new AgentExecution("exec-1", user, "req-1", clock,
                new AgentProperties(8, 10, 60, 2, 2000, 20));
    }
}
