package com.prince.agentic.agent;

/** Terminal agent run statuses (spec §15/§22). */
public enum AgentStatus { COMPLETED, FAILED, TIMED_OUT, CANCELLED, LIMIT_REACHED, LOOP_DETECTED }
