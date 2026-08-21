package com.prince.agentic.agent;

/** Cooperative cancellation seam (spec §10). Checked between steps; no hard interruption (M8). */
public interface CancellationToken {
    boolean isCancelled();
}
