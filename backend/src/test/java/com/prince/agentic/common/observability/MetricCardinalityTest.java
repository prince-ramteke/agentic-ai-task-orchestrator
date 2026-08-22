package com.prince.agentic.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the M10 cardinality rule (ADR-0030): no meter registered in this application may carry a
 * forbidden tag key. The test walks the entire {@link MeterRegistry} produced by a booted app and
 * fails if any meter's tag keys intersect the forbidden set.
 *
 * <p>We do not enumerate every possible tag key — that would drift; instead we assert on the small,
 * <b>enforced-forbidden</b> list from the spec. Adding a new forbidden key is a one-line change here
 * and a one-line change in {@code docs/OBSERVABILITY.md}.
 */
@SpringBootTest
@ActiveProfiles("test")
class MetricCardinalityTest {

    /** Every one of these would explode series cardinality (per-user / per-request / per-exec). */
    private static final Set<String> FORBIDDEN = Set.of(
            "userId", "user_id",
            "conversationId", "conversation_id",
            "executionId", "execution_id",
            "requestId", "request_id",
            "confirmationId", "confirmation_id",
            "arguments", "argumentsHash", "arguments_hash",
            "prompt", "promptText", "prompt_text"
    );

    @Autowired private MeterRegistry meters;

    @Test
    void noRegisteredMeterCarriesAForbiddenTagKey() {
        meters.forEachMeter(m -> {
            Set<String> keys = m.getId().getTags().stream().map(Tag::getKey).collect(java.util.stream.Collectors.toSet());
            Set<String> bad = new java.util.HashSet<>(keys);
            bad.retainAll(FORBIDDEN);
            assertThat(bad)
                    .withFailMessage("meter '%s' declared forbidden high-cardinality tag(s) %s (tags=%s)",
                            m.getId().getName(), bad, m.getId().getTags())
                    .isEmpty();
        });
    }
}
