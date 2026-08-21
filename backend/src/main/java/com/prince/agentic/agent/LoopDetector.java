package com.prince.agentic.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic loop detection by tool + canonical (key-sorted) arguments (spec §11). */
public class LoopDetector {

    private final ObjectMapper canonical;
    private final int threshold;
    private final Map<String, Integer> counts = new HashMap<>();

    public LoopDetector(ObjectMapper mapper, int threshold) {
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.threshold = threshold;
    }

    /** Record this call; return true when its fingerprint now exceeds the threshold. */
    public boolean isRepeat(String tool, Map<String, Object> args) {
        String fp = tool + "#" + fingerprint(args);
        int n = counts.merge(fp, 1, Integer::sum);
        return n > threshold;
    }

    private String fingerprint(Map<String, Object> args) {
        Map<String, Object> sorted = args == null ? Map.of() : new TreeMap<>(args);
        try {
            return canonical.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            return String.valueOf(sorted);
        }
    }
}
