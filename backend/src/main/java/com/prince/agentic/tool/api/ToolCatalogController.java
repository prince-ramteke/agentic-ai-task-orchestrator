package com.prince.agentic.tool.api;

import com.prince.agentic.tool.ToolRegistry;
import com.prince.agentic.tool.api.dto.ToolDescriptorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only, ADMIN-only catalog of registered tools (metadata only). For developer/operator
 * inspection and regression-testing that the registry is wired as expected. It exposes no
 * implementation class names, no secrets, and no execution capability — the agent path is M6.
 */
@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "Tools", description = "Registered tool metadata (ADMIN only; read-only)")
@SecurityRequirement(name = "bearerAuth")
public class ToolCatalogController {

    private final ToolRegistry registry;

    public ToolCatalogController(ToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List registered tool descriptors (metadata only)")
    public List<ToolDescriptorResponse> list() {
        return registry.descriptors().stream().map(ToolDescriptorResponse::from).toList();
    }
}
