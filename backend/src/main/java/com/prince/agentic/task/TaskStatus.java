package com.prince.agentic.task;

/** Task lifecycle states. M3 stores this as a validated enum; no transition state machine (see ADR-0006). */
public enum TaskStatus { TODO, IN_PROGRESS, COMPLETED, CANCELLED }
