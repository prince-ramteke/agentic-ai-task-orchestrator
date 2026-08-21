package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LoopDetectorTest {

    private LoopDetector detector() { return new LoopDetector(new ObjectMapper(), 2); }

    @Test
    void repeats_beyondThreshold_areDetected() {
        LoopDetector d = detector();
        assertThat(d.isRepeat("task.search", Map.of("priority", "HIGH"))).isFalse(); // 1st
        assertThat(d.isRepeat("task.search", Map.of("priority", "HIGH"))).isFalse(); // 2nd (== threshold)
        assertThat(d.isRepeat("task.search", Map.of("priority", "HIGH"))).isTrue();  // 3rd (> threshold)
    }

    @Test
    void differentArgs_areNotARepeat() {
        LoopDetector d = detector();
        assertThat(d.isRepeat("task.search", Map.of("priority", "HIGH"))).isFalse();
        assertThat(d.isRepeat("task.search", Map.of("priority", "LOW"))).isFalse();
    }

    @Test
    void argumentKeyOrder_doesNotDefeatDetection() {
        LoopDetector d = detector();
        Map<String,Object> a = new LinkedHashMap<>(); a.put("x", 1); a.put("y", 2);
        Map<String,Object> b = new LinkedHashMap<>(); b.put("y", 2); b.put("x", 1);
        d.isRepeat("t", a);
        d.isRepeat("t", b);
        assertThat(d.isRepeat("t", a)).isTrue(); // 3rd occurrence of same canonical fingerprint
    }
}
