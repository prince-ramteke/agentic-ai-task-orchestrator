package com.prince.agentic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Agentic AI Task Orchestrator backend.
 *
 * <p>Milestone 1 establishes only the backend foundation: web layer, configuration,
 * health, error handling, and OpenAPI. Domain, persistence, security, and the agent
 * runtime arrive in later milestones (see {@code docs/ROADMAP.md}).
 */
@SpringBootApplication
public class AgenticApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticApplication.class, args);
    }
}
