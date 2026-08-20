package com.prince.agentic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the full Spring application context starts and wires successfully
 * (all beans, configuration, and auto-configuration) with no external infrastructure.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgenticApplicationTests {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails — that is the assertion.
    }
}
