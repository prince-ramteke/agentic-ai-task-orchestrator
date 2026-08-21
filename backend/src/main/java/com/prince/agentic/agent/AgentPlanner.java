package com.prince.agentic.agent;

import com.prince.agentic.agent.exception.AgentInvalidDecisionException;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import org.springframework.stereotype.Service;

import java.util.List;

/** One decision step: render → generateStructured(AgentDecision) → validate → one bounded repair. */
@Service
public class AgentPlanner {

    private final LlmClient llm;
    private final AgentPromptService prompts;
    private final AgentDecisionValidator validator;
    private final AgentToolCatalog catalog;

    public AgentPlanner(LlmClient llm, AgentPromptService prompts,
                        AgentDecisionValidator validator, AgentToolCatalog catalog) {
        this.llm = llm;
        this.prompts = prompts;
        this.validator = validator;
        this.catalog = catalog;
    }

    public AgentDecision decide(String userMessage, List<AgentObservation> observations,
                                int iterationsLeft, int toolCallsLeft) {
        String prompt = prompts.render(userMessage, catalog.render(), observations, iterationsLeft, toolCallsLeft);
        AgentDecision d = attempt(prompt);
        if (!validator.isValid(d)) {
            String repair = prompt + "\n\nYour previous answer was invalid. "
                    + "Return exactly one decision: FINAL with a response, or TOOL_CALL with a registered tool and arguments.";
            d = attempt(repair);
            if (!validator.isValid(d)) {
                throw new AgentInvalidDecisionException("Model produced an invalid decision after one repair.");
            }
        }
        return d;
    }

    /** Mirror AiService.attempt: a thrown invalid-output becomes a null decision so validate/repair
     *  handles thrown-and-returned uniformly. Provider/timeout/unavailable errors propagate. */
    private AgentDecision attempt(String prompt) {
        try {
            return llm.generateStructured(prompt, AgentDecision.class);
        } catch (LlmInvalidOutputException parseFailure) {
            return null;
        }
    }
}
