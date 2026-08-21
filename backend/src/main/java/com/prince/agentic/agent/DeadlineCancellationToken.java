package com.prince.agentic.agent;

import java.time.Clock;
import java.time.Instant;

/** Unifies wall-clock deadline and explicit cancellation behind one cooperative check. */
public class DeadlineCancellationToken implements CancellationToken {

    private final Clock clock;
    private final Instant deadline;
    private volatile boolean cancelled;

    public DeadlineCancellationToken(Clock clock, Instant deadline) {
        this.clock = clock;
        this.deadline = deadline;
    }

    /** External cancel seam (M8 orchestrator hook). */
    public void cancel() { this.cancelled = true; }

    @Override
    public boolean isCancelled() {
        return cancelled || !clock.instant().isBefore(deadline);
    }
}
