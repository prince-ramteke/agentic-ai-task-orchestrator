package com.prince.agentic.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the M7 memory boundary (spec §2): the memory module is pure infrastructure over
 * application-owned DTOs. It must not depend on Spring AI, on domain services, on tools, or on
 * persistence internals — it operates only on its own records and the authenticated principal.
 * A source-scan keeps this dependency-free and fast.
 */
class MemoryArchitectureBoundaryTest {

    private static final Path MEMORY_ROOT = Path.of("src/main/java/com/prince/agentic/memory");

    @Test
    void memoryPackage_staysIndependentOfAiToolsAndDomain() throws Exception {
        try (Stream<Path> files = Files.walk(MEMORY_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String src;
                try {
                    src = Files.readString(p);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertThat(src)
                        .as("%s must stay independent of AI/tools/domain/persistence", p)
                        .doesNotContain("org.springframework.ai.")
                        .doesNotContain("com.prince.agentic.tool.")
                        .doesNotContain("com.prince.agentic.task.")
                        .doesNotContain("com.prince.agentic.customer.")
                        .doesNotContain("jakarta.persistence.")
                        .doesNotContain("Repository;");
            });
        }
    }
}
