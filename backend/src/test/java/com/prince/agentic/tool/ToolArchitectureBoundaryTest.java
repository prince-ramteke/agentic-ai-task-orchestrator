package com.prince.agentic.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces M5 isolation by scanning the tool subsystem's sources: the deterministic tool framework
 * must not depend on the AI layer or on persistence internals. Domain tools reach data only through
 * {@code TaskService}/{@code CustomerService}. A source-scan keeps this dependency-free and fast.
 */
class ToolArchitectureBoundaryTest {

    private static final Path TOOL_ROOT = Path.of("src/main/java/com/prince/agentic/tool");

    @Test
    void tool_subsystem_does_not_depend_on_ai_or_persistence() throws Exception {
        try (Stream<Path> files = Files.walk(TOOL_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String src;
                try {
                    src = Files.readString(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertThat(src)
                        .as("%s must not depend on the AI layer or persistence internals", p)
                        .doesNotContain("com.prince.agentic.ai")
                        .doesNotContain("org.springframework.ai")
                        .doesNotContain("jakarta.persistence.EntityManager")
                        .doesNotContain("org.springframework.jdbc.core.JdbcTemplate")
                        .doesNotContain("Repository;");   // no direct repository imports
            });
        }
    }
}
