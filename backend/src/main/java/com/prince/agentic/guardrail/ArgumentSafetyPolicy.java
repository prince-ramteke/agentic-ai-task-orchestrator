package com.prince.agentic.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Policy-level argument safety (spec §4, §5), <b>on top of</b> — never a replacement for — M5's
 * per-tool DTO binding and Bean Validation. It applies two modest, structural checks to the raw
 * proposed arguments:
 *
 * <ol>
 *   <li><b>Size</b> — the serialized argument map must not exceed {@code guardrail.max-argument-chars}.
 *       This bounds payloads regardless of the tool's own schema.</li>
 *   <li><b>Blatant control-injection markers</b> — a small, explicit deny-list of obvious
 *       instruction-override / code-execution phrases.</li>
 * </ol>
 *
 * <p><b>Honesty (spec §5):</b> this is a modest heuristic, <em>not</em> a claim to detect or defeat
 * prompt injection. The real boundary is structural: typed decisions, the tool allowlist, backend
 * identity, authorization, confirmation, and the executor. This policy just refuses the obviously
 * abusive before it reaches a tool.
 */
@Component
public class ArgumentSafetyPolicy implements GuardrailPolicy {

    static final int ORDER = 10;
    private static final String POLICY_ID = "argument-safety";

    /** Lower-cased blatant markers. Intentionally small and documented; not a security guarantee. */
    private static final String[] UNSAFE_MARKERS = {
            "ignore previous instructions",
            "ignore all previous instructions",
            "disregard the system prompt",
            "you are now",
            "system:",
            "rm -rf",
            "drop table",
            "; drop ",
    };

    private final ObjectMapper mapper;
    private final GuardrailProperties props;

    public ArgumentSafetyPolicy(ObjectMapper mapper, GuardrailProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public GuardrailDecision evaluate(GuardrailInput input) {
        Map<String, Object> args = input.decision().arguments();
        if (args == null || args.isEmpty()) {
            return GuardrailDecision.allow();
        }
        String serialized;
        try {
            serialized = mapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            return GuardrailDecision.deny("UNSAFE_ACTION",
                    "Arguments could not be safely serialized.", POLICY_ID);
        }
        if (serialized.length() > props.maxArgumentChars()) {
            return GuardrailDecision.deny("POLICY_VIOLATION",
                    "Arguments exceed the maximum allowed size.", POLICY_ID);
        }
        String haystack = serialized.toLowerCase();
        for (String marker : UNSAFE_MARKERS) {
            if (haystack.contains(marker)) {
                return GuardrailDecision.deny("UNSAFE_ACTION",
                        "Arguments contain a disallowed pattern.", POLICY_ID);
            }
        }
        return GuardrailDecision.allow();
    }

    @Override
    public int order() {
        return ORDER;
    }
}
