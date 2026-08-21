package com.prince.agentic.agent;

import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * M6-owned adapter (spec §14): renders the registry's tools into a model-readable catalog by
 * reflecting over each descriptor's inputType record components. M5 stays free of agent/Spring AI.
 */
@Component
public class AgentToolCatalog {

    private final List<AgentToolDefinition> definitions;

    public AgentToolCatalog(ToolRegistry registry) {
        List<AgentToolDefinition> defs = new ArrayList<>();
        for (ToolDescriptor d : registry.descriptors()) {
            defs.add(new AgentToolDefinition(
                    d.name(), d.description(), d.category(), d.risk().name(), fieldsOf(d.inputType())));
        }
        this.definitions = List.copyOf(defs);
    }

    public List<AgentToolDefinition> definitions() { return definitions; }

    private List<AgentToolDefinition.FieldDef> fieldsOf(Class<?> inputType) {
        List<AgentToolDefinition.FieldDef> fields = new ArrayList<>();
        if (inputType.isRecord()) {
            for (RecordComponent rc : inputType.getRecordComponents()) {
                List<String> allowed = rc.getType().isEnum()
                        ? List.of(enumNames(rc.getType())) : List.of();
                fields.add(new AgentToolDefinition.FieldDef(
                        rc.getName(), rc.getType().getSimpleName(), allowed));
            }
        }
        return fields;
    }

    private String[] enumNames(Class<?> e) {
        Object[] cs = e.getEnumConstants();
        String[] names = new String[cs.length];
        for (int i = 0; i < cs.length; i++) names[i] = ((Enum<?>) cs[i]).name();
        return names;
    }

    /** Stable, human/model-readable catalog block for the prompt. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (AgentToolDefinition d : definitions) {
            sb.append("- ").append(d.name()).append(" [").append(d.risk()).append("]: ")
              .append(d.description()).append('\n');
            for (AgentToolDefinition.FieldDef f : d.fields()) {
                sb.append("    ").append(f.name()).append(": ").append(f.type());
                if (!f.allowedValues().isEmpty()) sb.append(" one of ").append(f.allowedValues());
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
