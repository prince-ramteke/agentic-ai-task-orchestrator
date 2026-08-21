package com.prince.agentic.ai.support;

import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.LlmProviderInfo;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;

/**
 * Deterministic {@link LlmClient} for tests: no network, selectable failure modes.
 *
 * <p>This is the key payoff of the {@link LlmClient} abstraction — the whole AI layer (service,
 * controller, Spring context) is testable without a running Ollama or any model download.
 *
 * <ul>
 *   <li>{@code VALID} — returns canned text and the configured structured object (which may be an
 *       intentionally-invalid record, or {@code null}, to drive the untrusted-output path).</li>
 *   <li>{@code TIMEOUT} / {@code UNAVAILABLE} / {@code PROVIDER_ERROR} — throw the matching
 *       {@code Llm*Exception} on any call.</li>
 * </ul>
 */
public class FakeLlmClient implements LlmClient {

    public enum Mode { VALID, TIMEOUT, UNAVAILABLE, PROVIDER_ERROR }

    private Mode mode = Mode.VALID;
    private String text = "This is a deterministic fake completion.";
    private Object structured;   // returned by generateStructured in VALID mode (may be null)

    public FakeLlmClient setMode(Mode mode) { this.mode = mode; return this; }
    public FakeLlmClient setText(String text) { this.text = text; return this; }
    public FakeLlmClient setStructured(Object structured) { this.structured = structured; return this; }

    @Override
    public String generate(String prompt) {
        failIfConfigured();
        return text;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T generateStructured(String prompt, Class<T> type) {
        failIfConfigured();
        return (T) structured;   // null or an invalid record exercises the validation/repair path
    }

    @Override
    public LlmProviderInfo info() {
        return new LlmProviderInfo("fake", "fake-model");
    }

    private void failIfConfigured() {
        switch (mode) {
            case TIMEOUT -> throw new LlmTimeoutException("fake timeout");
            case UNAVAILABLE -> throw new LlmUnavailableException("fake unavailable");
            case PROVIDER_ERROR -> throw new LlmProviderException("fake provider error", null);
            case VALID -> { /* no-op */ }
        }
    }
}
