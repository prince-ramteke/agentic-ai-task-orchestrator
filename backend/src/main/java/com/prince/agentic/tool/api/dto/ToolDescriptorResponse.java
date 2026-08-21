package com.prince.agentic.tool.api.dto;

import com.prince.agentic.tool.ToolDescriptor;

import java.util.List;

/**
 * Metadata-only projection of a {@link ToolDescriptor} for the ADMIN catalog endpoint.
 * Intentionally exposes no implementation class name — input/output are rendered as simple type names.
 */
public record ToolDescriptorResponse(
        String name,
        String description,
        String category,
        String version,
        String risk,
        boolean requiresAuthentication,
        List<String> requiredRoles,
        String inputType,
        String outputType) {

    public static ToolDescriptorResponse from(ToolDescriptor d) {
        return new ToolDescriptorResponse(
                d.name(), d.description(), d.category(), d.version(),
                d.risk().name(), d.requiresAuthentication(), List.copyOf(d.requiredRoles()),
                d.inputType().getSimpleName(), d.outputType().getSimpleName());
    }
}
