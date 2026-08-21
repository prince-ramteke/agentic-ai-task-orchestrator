package com.prince.agentic.tool;

import com.prince.agentic.tool.exception.ToolRegistrationException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of all tools, built once from every {@link Tool} bean at startup.
 *
 * <p>It is <b>fail-fast</b> (an invalid or duplicate tool throws {@link ToolRegistrationException},
 * failing application boot — no partially-valid registry can start) and <b>immutable</b> afterward
 * (an unmodifiable map), giving O(1) lookup by name and inherent thread-safety for the concurrent
 * access the future agent (M6) will bring. No runtime registration; no dynamic plugin loading.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool<?, ?>> byName;
    private final List<ToolDescriptor> descriptors;

    public ToolRegistry(List<Tool<?, ?>> tools) {
        Map<String, Tool<?, ?>> map = new LinkedHashMap<>();
        for (Tool<?, ?> tool : tools) {
            ToolDescriptor d = requireValid(tool);
            if (map.putIfAbsent(d.name(), tool) != null) {
                throw new ToolRegistrationException("duplicate tool name: " + d.name());
            }
        }
        this.byName = Map.copyOf(map);
        this.descriptors = tools.stream()
                .map(Tool::descriptor)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
    }

    private ToolDescriptor requireValid(Tool<?, ?> tool) {
        if (tool == null) {
            throw new ToolRegistrationException("null tool bean");
        }
        ToolDescriptor d = tool.descriptor();
        if (d == null) {
            throw new ToolRegistrationException("tool has null descriptor: " + tool.getClass().getName());
        }
        // The descriptor's compact constructor already validated name/description/risk/types/timeout/roles;
        // here we additionally enforce the role naming convention required for authorization.
        for (String role : d.requiredRoles()) {
            if (role == null || !role.startsWith("ROLE_")) {
                throw new ToolRegistrationException("invalid role '" + role + "' on tool " + d.name());
            }
        }
        return d;
    }

    /** Resolve a tool by name, or {@code null} if none is registered (the executor maps null → TOOL_NOT_FOUND). */
    public Tool<?, ?> resolve(String name) {
        return byName.get(name);
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    /** Immutable, name-sorted view of all registered descriptors (metadata only). */
    public List<ToolDescriptor> descriptors() {
        return descriptors;
    }

    public int size() {
        return byName.size();
    }
}
