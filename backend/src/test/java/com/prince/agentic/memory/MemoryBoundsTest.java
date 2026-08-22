package com.prince.agentic.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryBoundsTest {

    private static final Instant T = Instant.parse("2026-08-22T00:00:00Z");

    private List<MemoryMessage> messages(int n) {
        List<MemoryMessage> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(MemoryMessage.user("m" + i, i, T));
        }
        return list;
    }

    // --- storage trim ---------------------------------------------------------

    @Test
    void trimForStorage_keepsLatestByMessageCount() {
        List<MemoryMessage> kept = MemoryBounds.trimForStorage(messages(5), 2, 10_000);
        assertThat(kept).hasSize(2);
        assertThat(kept.get(0).content()).isEqualTo("m3");
        assertThat(kept.get(1).content()).isEqualTo("m4");
    }

    @Test
    void trimForStorage_keepsLatestByCharBudget() {
        // each content is exactly 2 chars ("m0".."m9"); a 5-char budget fits 2 newest (4 chars).
        List<MemoryMessage> kept = MemoryBounds.trimForStorage(messages(9), 50, 5);
        assertThat(kept).hasSize(2);
        assertThat(kept.get(1).content()).isEqualTo("m8");
    }

    @Test
    void trimForStorage_alwaysKeepsNewest_evenWhenItAloneExceedsCharBudget() {
        List<MemoryMessage> msgs = List.of(
                MemoryMessage.user("old", 0, T),
                MemoryMessage.assistant("a-very-long-newest-message", 1, T));
        List<MemoryMessage> kept = MemoryBounds.trimForStorage(msgs, 50, 3);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).content()).isEqualTo("a-very-long-newest-message");
    }

    @Test
    void trimForStorage_emptyInput_returnsEmpty() {
        assertThat(MemoryBounds.trimForStorage(List.of(), 10, 10)).isEmpty();
    }

    // --- context render -------------------------------------------------------

    @Test
    void renderContext_empty_returnsNone() {
        assertThat(MemoryBounds.renderContext(List.of(), 12, 6000)).isEqualTo("(none)");
    }

    @Test
    void renderContext_delimitsRolesAndKeepsLatest() {
        List<MemoryMessage> msgs = List.of(
                MemoryMessage.user("show my high priority tasks", 0, T),
                MemoryMessage.tool("task.search", "found 3 high-priority tasks", 1, T),
                MemoryMessage.assistant("You have 3.", 2, T));
        String rendered = MemoryBounds.renderContext(msgs, 12, 6000);
        assertThat(rendered).isEqualTo(
                "USER: show my high priority tasks\n"
                        + "TOOL(task.search): found 3 high-priority tasks\n"
                        + "ASSISTANT: You have 3.");
    }

    @Test
    void renderContext_respectsMessageBound_keepingNewest() {
        List<MemoryMessage> msgs = List.of(
                MemoryMessage.user("first", 0, T),
                MemoryMessage.assistant("second", 1, T),
                MemoryMessage.user("third", 2, T));
        String rendered = MemoryBounds.renderContext(msgs, 1, 6000);
        assertThat(rendered).isEqualTo("USER: third");
    }
}
