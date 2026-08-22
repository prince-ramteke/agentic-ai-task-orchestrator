package com.prince.agentic.guardrail;

import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;

import java.time.Duration;
import java.util.Set;

/** Test fixtures: minimal tools/descriptors at each risk level. */
final class GuardrailTestTools {

    private GuardrailTestTools() {
    }

    record Args(String value) {
    }

    static ToolDescriptor descriptor(String name, ToolRiskLevel risk) {
        return new ToolDescriptor(name, "test tool " + name, "test", "1", risk, true,
                Set.of("ROLE_USER"), Args.class, Args.class, Duration.ofSeconds(5));
    }

    static Tool<Args, Args> tool(String name, ToolRiskLevel risk) {
        ToolDescriptor d = descriptor(name, risk);
        return new Tool<>() {
            @Override
            public ToolDescriptor descriptor() {
                return d;
            }

            @Override
            public Args execute(ToolExecutionContext context, Args input) {
                return input;
            }
        };
    }
}
