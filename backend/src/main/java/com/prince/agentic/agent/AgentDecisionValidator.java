package com.prince.agentic.agent;

import org.springframework.stereotype.Component;

/** Cross-field validity of an AgentDecision envelope (spec §6). Does NOT check tool existence
 *  or argument schema — that is the ToolExecutor's job (two-level validation). */
@Component
public class AgentDecisionValidator {

    public boolean isValid(AgentDecision d) {
        if (d == null || d.action() == null) return false;
        return switch (d.action()) {
            case FINAL -> notBlank(d.response()) && blank(d.tool());
            case TOOL_CALL -> notBlank(d.tool()) && blank(d.response());
        };
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private boolean blank(String s) { return s == null || s.isBlank(); }
}
