package com.prince.agentic.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full app-context smoke tests for the M10 observability surface (ADR-0030):
 * <ul>
 *   <li>{@code /actuator/prometheus} is anonymous-accessible and returns Micrometer output;</li>
 *   <li>{@code X-Request-Id} is minted on every response and echoed back when a valid UUID is
 *       supplied by the caller;</li>
 *   <li>a junk header value is discarded (defense against MDC/log injection).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityEndpointsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void prometheusEndpoint_isAnonymouslyReachable_andEmitsMicrometerOutput() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        // Prometheus text format always contains at least one of the JVM baseline metrics.
        assertThat(body).contains("jvm_");
    }

    @Test
    void healthReadiness_probeIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void healthLiveness_probeIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void requestId_isMintedWhenMissing() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        String id = res.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        UUID.fromString(id); // parses
    }

    @Test
    void requestId_echoesSuppliedUuid() throws Exception {
        String supplied = UUID.randomUUID().toString();
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", supplied))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", supplied));
    }

    @Test
    void requestId_rejectsJunk_andMintsFresh() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/health").header("X-Request-Id", "not-a-uuid"))
                .andExpect(status().isOk())
                .andReturn();
        String id = res.getResponse().getHeader("X-Request-Id");
        assertThat(id).isNotEqualTo("not-a-uuid");
        UUID.fromString(id);
    }
}
