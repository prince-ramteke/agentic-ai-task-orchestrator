package com.prince.agentic.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintServiceTest {

    private final FingerprintService fp = new FingerprintService(new ObjectMapper());

    private String fingerprint(long user, String conv, String tool, Map<String, Object> args, ToolRiskLevel risk) {
        return fp.fingerprint(user, conv, tool, fp.canonicalArguments(args), risk);
    }

    @Test
    void identicalInput_producesIdenticalFingerprint() {
        String a = fingerprint(1L, "c1", "task.create", Map.of("title", "x"), ToolRiskLevel.SIDE_EFFECTING);
        String b = fingerprint(1L, "c1", "task.create", Map.of("title", "x"), ToolRiskLevel.SIDE_EFFECTING);
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void argumentOrder_doesNotChangeFingerprint() {
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("a", 1);
        m1.put("b", 2);
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("b", 2);
        m2.put("a", 1);
        assertThat(fingerprint(1L, "c", "t", m1, ToolRiskLevel.SIDE_EFFECTING))
                .isEqualTo(fingerprint(1L, "c", "t", m2, ToolRiskLevel.SIDE_EFFECTING));
    }

    @Test
    void differentUser_changesFingerprint() {
        assertThat(fingerprint(1L, "c", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING))
                .isNotEqualTo(fingerprint(2L, "c", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING));
    }

    @Test
    void differentConversation_changesFingerprint() {
        assertThat(fingerprint(1L, "c1", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING))
                .isNotEqualTo(fingerprint(1L, "c2", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING));
    }

    @Test
    void differentTool_changesFingerprint() {
        assertThat(fingerprint(1L, "c", "task.create", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING))
                .isNotEqualTo(fingerprint(1L, "c", "task.delete", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING));
    }

    @Test
    void differentArguments_changesFingerprint() {
        assertThat(fingerprint(1L, "c", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING))
                .isNotEqualTo(fingerprint(1L, "c", "t", Map.of("x", 2), ToolRiskLevel.SIDE_EFFECTING));
    }

    @Test
    void differentRisk_changesFingerprint() {
        assertThat(fingerprint(1L, "c", "t", Map.of("x", 1), ToolRiskLevel.SIDE_EFFECTING))
                .isNotEqualTo(fingerprint(1L, "c", "t", Map.of("x", 1), ToolRiskLevel.HIGH_RISK));
    }
}
