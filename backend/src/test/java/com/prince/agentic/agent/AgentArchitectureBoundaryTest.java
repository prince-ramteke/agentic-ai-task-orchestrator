package com.prince.agentic.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the M6 agent boundary by scanning the agent subsystem's sources: the orchestrator must
 * not depend on Spring AI directly, on persistence internals, or on domain services — its only path
 * to effects is {@code ToolExecutor} (spec Global Constraints). It legitimately depends on the
 * approved LLM provider abstraction ({@code com.prince.agentic.ai.llm.LlmClient} and its exception
 * hierarchy), so only the specific forbidden substrings below are checked — not the whole
 * {@code com.prince.agentic.ai} package. A source-scan keeps this dependency-free and fast.
 */
class AgentArchitectureBoundaryTest {

    private static final Path AGENT_ROOT = Path.of("src/main/java/com/prince/agentic/agent");

    @Test
    void agentPackage_doesNotBypassToolBoundary() throws Exception {
        try (Stream<Path> files = Files.walk(AGENT_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String src;
                try {
                    src = Files.readString(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertThat(src)
                        .as("%s must not bypass the ToolExecutor boundary", p)
                        .doesNotContain("org.springframework.ai.")
                        .doesNotContain("jakarta.persistence.EntityManager")
                        .doesNotContain("org.springframework.jdbc.core.JdbcTemplate")
                        .doesNotContain("com.prince.agentic.task.TaskService")
                        .doesNotContain("com.prince.agentic.customer.CustomerService")
                        .doesNotContain("Repository;");   // no direct repository imports
            });
        }
    }
}
