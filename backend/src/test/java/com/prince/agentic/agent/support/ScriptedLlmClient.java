package com.prince.agentic.agent.support;

import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.LlmProviderInfo;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Sequence-aware LlmClient test double for multi-step agent tests (spec §19). */
public class ScriptedLlmClient implements LlmClient {

    public enum Mode { VALID, TIMEOUT, UNAVAILABLE, PROVIDER_ERROR }

    private Mode mode = Mode.VALID;
    private final Deque<Object> structured = new ArrayDeque<>();
    private final List<String> prompts = new ArrayList<>();

    public ScriptedLlmClient enqueueStructured(Object... items) {
        for (Object i : items) structured.add(i);
        return this;
    }
    public ScriptedLlmClient setMode(Mode m) { this.mode = m; return this; }
    public List<String> prompts() { return prompts; }

    /**
     * Resets the shared singleton bean to a clean state: clears the scripted-decision queue and
     * captured prompts, and restores {@link Mode#VALID}. Call this in a {@code @BeforeEach} in any
     * test class that injects this bean — it lives for the whole Spring context, not per test.
     */
    public void reset() {
        structured.clear();
        prompts.clear();
        mode = Mode.VALID;
    }

    @Override public String generate(String prompt) { prompts.add(prompt); failIfConfigured(); return "text"; }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T generateStructured(String prompt, Class<T> type) {
        prompts.add(prompt);
        failIfConfigured();
        if (structured.isEmpty()) throw new IllegalStateException("no scripted decision left");
        return (T) structured.poll();
    }

    @Override public LlmProviderInfo info() { return new LlmProviderInfo("scripted", "scripted-model"); }

    private void failIfConfigured() {
        switch (mode) {
            case TIMEOUT -> throw new LlmTimeoutException("scripted timeout");
            case UNAVAILABLE -> throw new LlmUnavailableException("scripted unavailable");
            case PROVIDER_ERROR -> throw new LlmProviderException("scripted provider error", null);
            case VALID -> { }
        }
    }
}
