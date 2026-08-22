package com.prince.agentic.memory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Deterministic, pure bounding of conversation history (spec §1, §3). Two boundaries:
 * <ul>
 *   <li>{@link #trimForStorage} — what Redis keeps (storage bounds), measured by raw content length.</li>
 *   <li>{@link #renderContext} — the smaller slice sent to the LLM (context bounds), measured by
 *       rendered-line length. The full stored history is never handed to the model.</li>
 * </ul>
 * Both keep the <b>latest</b> messages and always retain at least the single newest message, so a
 * current turn is never silently dropped even if it alone exceeds a char budget.
 */
public final class MemoryBounds {

    private MemoryBounds() {
    }

    public static List<MemoryMessage> trimForStorage(List<MemoryMessage> messages,
                                                     int maxMessages, int maxChars) {
        return latest(messages, maxMessages, maxChars,
                m -> m.content() == null ? 0 : m.content().length());
    }

    /** Delimited, LLM-facing history string. Returns {@code "(none)"} when there is nothing to show. */
    public static String renderContext(List<MemoryMessage> messages, int maxMessages, int maxChars) {
        List<MemoryMessage> kept = latest(messages, maxMessages, maxChars, m -> line(m).length());
        if (kept.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryMessage m : kept) {
            sb.append(line(m)).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String line(MemoryMessage m) {
        String who = (m.role() == MemoryRole.TOOL && m.tool() != null)
                ? "TOOL(" + m.tool() + ")"
                : m.role().name();
        return who + ": " + (m.content() == null ? "" : m.content());
    }

    /** Keep the newest messages within both bounds; the single newest is always retained. */
    private static List<MemoryMessage> latest(List<MemoryMessage> messages, int maxMessages,
                                              int maxChars, ToIntFunction<MemoryMessage> size) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Deque<MemoryMessage> kept = new ArrayDeque<>();
        int chars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MemoryMessage m = messages.get(i);
            int s = size.applyAsInt(m);
            boolean first = kept.isEmpty();
            if (!first && (kept.size() >= maxMessages || chars + s > maxChars)) {
                break;
            }
            kept.addFirst(m);
            chars += s;
        }
        return List.copyOf(kept);
    }
}
