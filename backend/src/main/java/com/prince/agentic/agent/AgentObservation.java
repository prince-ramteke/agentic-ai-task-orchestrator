package com.prince.agentic.agent;

/** Bounded, model-safe view of a tool's outcome, fed back into the next planning step (spec §12). */
public record AgentObservation(String tool, boolean success, String resultSummary, String errorCode) {}
