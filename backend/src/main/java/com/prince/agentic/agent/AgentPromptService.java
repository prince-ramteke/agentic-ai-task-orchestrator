package com.prince.agentic.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Renders the versioned agent prompt; untrusted text only in delimited slots (spec §13). */
@Service
public class AgentPromptService {

    private final String template;

    public AgentPromptService(@Value("classpath:prompts/agent-system.st") Resource tmpl) {
        this.template = read(tmpl);
    }

    // No-arg constructor for unit tests that don't load the classpath resource.
    AgentPromptService() { this.template = defaultTemplate(); }

    public String render(String userMessage, String history, String toolCatalog,
                         List<AgentObservation> observations, int iterationsLeft, int toolCallsLeft) {
        return template
                .replace("{tools}", safe(toolCatalog))
                .replace("{history}", renderHistory(history))
                .replace("{request}", safe(userMessage))
                .replace("{observations}", renderObservations(observations))
                .replace("{iterationsLeft}", Integer.toString(iterationsLeft))
                .replace("{toolCallsLeft}", Integer.toString(toolCallsLeft));
    }

    /** Prior-turn context in the delimited slot only. Blank/absent memory renders as "(none)". */
    private String renderHistory(String history) {
        return (history == null || history.isBlank()) ? "(none)" : history;
    }

    private String renderObservations(List<AgentObservation> obs) {
        if (obs == null || obs.isEmpty()) return "(none yet)";
        StringBuilder sb = new StringBuilder();
        for (AgentObservation o : obs) {
            sb.append(o.tool()).append(o.success() ? " OK " : " ERR ")
              .append(o.success() ? o.resultSummary() : o.errorCode() + ": " + o.resultSummary())
              .append('\n');
        }
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String read(Resource r) {
        try { return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException("prompt load failed", e); }
    }

    private String defaultTemplate() {
        return "TOOLS:\n{tools}\nHISTORY:\n{history}\nREQUEST:\n{request}\nOBS:\n{observations}\n"
             + "left it={iterationsLeft} tc={toolCallsLeft}";
    }
}
