package com.prince.agentic.ai;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the M4 isolation rules by scanning the AI feature's sources:
 * <ul>
 *   <li>the AI layer must not depend on the Task/Customer domains or on persistence, and</li>
 *   <li>only the {@code ai.llm.ollama} + {@code ai.config} packages may import Spring AI —
 *       everything else depends on the provider-agnostic {@code LlmClient} abstraction.</li>
 * </ul>
 * A source-scan keeps this dependency-free and fast.
 */
class ArchitectureBoundaryTest {

    private static final Path AI_ROOT = Path.of("src/main/java/com/prince/agentic/ai");

    @Test
    void ai_package_does_not_depend_on_domain_or_persistence() throws Exception {
        forEachAiSource((path, src) -> assertThat(src)
                .as("%s must not depend on the Task/Customer domains or persistence", path)
                .doesNotContain("com.prince.agentic.task")
                .doesNotContain("com.prince.agentic.customer")
                .doesNotContain("jakarta.persistence.EntityManager")
                .doesNotContain("org.springframework.jdbc.core.JdbcTemplate"));
    }

    @Test
    void only_ollama_and_config_packages_import_spring_ai() throws Exception {
        forEachAiSource((path, src) -> {
            String normalized = path.toString().replace('\\', '/');
            boolean allowedToUseSpringAi =
                    normalized.contains("/ai/llm/ollama/") || normalized.contains("/ai/config/");
            if (!allowedToUseSpringAi) {
                assertThat(src)
                        .as("%s must not import org.springframework.ai.* (use the LlmClient abstraction)", path)
                        .doesNotContain("org.springframework.ai");
            }
        });
    }

    private interface SourceCheck {
        void check(Path path, String source);
    }

    private void forEachAiSource(SourceCheck check) throws Exception {
        try (Stream<Path> files = Files.walk(AI_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    check.check(p, Files.readString(p));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
