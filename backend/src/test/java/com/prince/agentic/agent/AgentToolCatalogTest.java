package com.prince.agentic.agent;

import com.prince.agentic.task.TaskService;
import com.prince.agentic.tool.ToolRegistry;
import com.prince.agentic.tool.task.TaskSearchTool;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentToolCatalogTest {

    private final ToolRegistry registry =
            new ToolRegistry(List.of(new TaskSearchTool(mock(TaskService.class))));
    private final AgentToolCatalog catalog = new AgentToolCatalog(registry);

    @Test
    void definitions_areDerivedFromRegistry_notHardcoded() {
        AgentToolDefinition def = catalog.definitions().stream()
                .filter(d -> d.name().equals("task.search")).findFirst().orElseThrow();
        assertThat(def.category()).isEqualTo("task");
        assertThat(def.risk()).isEqualTo("READ_ONLY");
        assertThat(def.fields()).extracting(AgentToolDefinition.FieldDef::name)
                .contains("status", "priority", "dueBefore", "page", "size");
    }

    @Test
    void enumFields_exposeAllowedValues() {
        // com.prince.agentic.task.TaskStatus is TODO, IN_PROGRESS, COMPLETED, CANCELLED (no DONE).
        AgentToolDefinition def = catalog.definitions().stream()
                .filter(d -> d.name().equals("task.search")).findFirst().orElseThrow();
        AgentToolDefinition.FieldDef status = def.fields().stream()
                .filter(f -> f.name().equals("status")).findFirst().orElseThrow();
        assertThat(status.allowedValues()).contains("TODO", "IN_PROGRESS", "COMPLETED");
    }

    @Test
    void render_producesStableTextWithToolNames() {
        assertThat(catalog.render()).contains("task.search");
    }
}
