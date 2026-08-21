package com.prince.agentic.agent;

import java.util.List;

/**
 * Model-readable description of one registered tool (spec §14), derived reflectively from its
 * {@code ToolDescriptor} and {@code inputType} record components by {@link AgentToolCatalog}.
 */
public record AgentToolDefinition(
        String name, String description, String category, String risk, List<FieldDef> fields) {
    public record FieldDef(String name, String type, List<String> allowedValues) {}
}
