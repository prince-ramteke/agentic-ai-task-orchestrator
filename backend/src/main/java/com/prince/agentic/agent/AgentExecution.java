package com.prince.agentic.agent;

import com.prince.agentic.security.AuthenticatedUser;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Mutable, single-request agent run state (spec §7). Not persisted (Redis/DB are M7/M9). */
public class AgentExecution {

    private final String executionId;
    private final AuthenticatedUser principal;
    private final String requestId;
    private final Instant startedAt;
    private final Instant deadline;
    private final DeadlineCancellationToken cancellation;
    private final List<AgentObservation> observations = new ArrayList<>();

    private int iteration;
    private int toolCallsUsed;

    public AgentExecution(String executionId, AuthenticatedUser principal, String requestId,
                          Clock clock, AgentProperties props) {
        this.executionId = executionId;
        this.principal = principal;
        this.requestId = requestId;
        this.startedAt = clock.instant();
        this.deadline = startedAt.plus(Duration.ofSeconds(props.timeoutSeconds()));
        this.cancellation = new DeadlineCancellationToken(clock, deadline);
    }

    public String executionId() { return executionId; }
    public AuthenticatedUser principal() { return principal; }
    public String requestId() { return requestId; }
    public Instant startedAt() { return startedAt; }
    public Instant deadline() { return deadline; }
    public CancellationToken cancellation() { return cancellation; }
    public int iteration() { return iteration; }
    public int toolCallsUsed() { return toolCallsUsed; }
    public List<AgentObservation> observations() { return List.copyOf(observations); }

    public int nextIteration() { return ++iteration; }
    public int recordToolCall() { return ++toolCallsUsed; }
    public void addObservation(AgentObservation o) { observations.add(o); }
    public long elapsedMillis(Clock clock) { return Duration.between(startedAt, clock.instant()).toMillis(); }
}
