package com.prince.agentic.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity-checks that configuration loads correctly under the test profile and that
 * build-time resource filtering populated a real application version (not the raw
 * {@code @project.version@} placeholder).
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationConfigTest {

    @Autowired
    private Environment environment;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.version}")
    private String applicationVersion;

    @Test
    void applicationName_loadsFromConfiguration() {
        assertThat(applicationName).isEqualTo("agentic-ai-task-orchestrator");
    }

    @Test
    void applicationVersion_isResolvedByResourceFiltering() {
        assertThat(applicationVersion)
                .isNotBlank()
                .doesNotContain("@")           // placeholder was substituted at build time
                .contains("0.0.1");
    }

    @Test
    void testProfile_isActive() {
        assertThat(environment.getActiveProfiles()).contains("test");
    }
}
