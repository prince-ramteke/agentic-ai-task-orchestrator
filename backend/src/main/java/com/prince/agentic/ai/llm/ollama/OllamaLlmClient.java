package com.prince.agentic.ai.llm.ollama;

import com.prince.agentic.ai.config.LlmProperties;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.LlmProviderInfo;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Ollama-backed {@link LlmClient} via Spring AI. This is the <b>only</b> class that imports Spring
 * AI types — the abstraction boundary keeps the vendor SDK out of the rest of the application
 * (guarded by {@code ArchitectureBoundaryTest}).
 *
 * <p>Structured output uses Spring AI's converter ({@code .entity(type)}), which injects the JSON
 * format instruction for {@code type}. All provider/transport failures are translated into the
 * application's {@link com.prince.agentic.ai.llm.exception.LlmException} hierarchy.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final ChatClient chat;
    private final LlmProperties props;

    public OllamaLlmClient(ChatClient chat, LlmProperties props) {
        this.chat = chat;
        this.props = props;
    }

    @Override
    public String generate(String prompt) {
        try {
            return chat.prompt().user(prompt).call().content();
        } catch (RuntimeException e) {
            throw map(e);
        }
    }

    @Override
    public <T> T generateStructured(String prompt, Class<T> type) {
        try {
            return chat.prompt().user(prompt).call().entity(type);
        } catch (RuntimeException e) {
            throw map(e);
        }
    }

    @Override
    public LlmProviderInfo info() {
        return new LlmProviderInfo("ollama", props.getOllama().getModel());
    }

    /**
     * Translate a provider/transport failure into the application's LLM exception model.
     * Package-private so it can be unit-tested without mocking the fluent ChatClient chain.
     */
    RuntimeException map(RuntimeException e) {
        Throwable root = rootCause(e);
        if (root instanceof SocketTimeoutException) {
            return new LlmTimeoutException("LLM request timed out");
        }
        if (root instanceof ConnectException || e instanceof ResourceAccessException) {
            return new LlmUnavailableException("LLM provider is unavailable");
        }
        // A structured-output conversion failure (Jackson) means the model emitted invalid output
        // (e.g. an out-of-range enum), NOT a provider fault. Classify it as invalid output so the
        // caller's bounded repair path handles it — the model, not the transport, is at fault.
        if (isStructuredParseFailure(e)) {
            return new LlmInvalidOutputException("Model produced unparseable or out-of-range structured output");
        }
        if (e instanceof RestClientException) {
            return new LlmProviderException("LLM provider error", e);
        }
        return new LlmProviderException("LLM call failed", e);
    }

    /** True if any cause in the chain is a Jackson (de)serialization failure from the output converter. */
    private boolean isStructuredParseFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getClass().getName().startsWith("com.fasterxml.jackson")) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
