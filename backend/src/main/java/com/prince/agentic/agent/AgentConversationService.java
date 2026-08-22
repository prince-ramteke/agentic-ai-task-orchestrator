package com.prince.agentic.agent;

import com.prince.agentic.memory.ConversationMemory;
import com.prince.agentic.memory.ConversationMemoryService;
import com.prince.agentic.memory.MemoryMessage;
import com.prince.agentic.memory.exception.MemoryUnavailableException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the M6 {@link AgentOrchestrator} with M7 conversation memory (spec §4): load bounded history →
 * run the (Redis-free) orchestrator with that history → append the bounded turn (USER + TOOL summaries
 * + ASSISTANT) with a refreshed TTL.
 *
 * <p><b>Hybrid failure policy.</b> Loading an existing conversation is fail-closed: a
 * {@link MemoryUnavailableException} propagates as 503 <em>before</em> any tool runs. Persistence is
 * best-effort: if the post-run append fails, the turn's result is still returned with
 * {@link MemoryStatus#UNAVAILABLE}. A new conversation whose append fails degrades to a stateless
 * turn ({@code conversationId=null}).
 *
 * <p>Identity always comes from the authenticated principal; {@code conversationId} is never an
 * authorization claim (ownership is enforced inside {@link ConversationMemoryService}).
 */
@Service
public class AgentConversationService {

    private static final Logger log = LoggerFactory.getLogger(AgentConversationService.class);

    private final AgentOrchestrator orchestrator;
    private final ConversationMemoryService memory;
    private final Clock clock;
    private final MeterRegistry meters;

    public AgentConversationService(AgentOrchestrator orchestrator, ConversationMemoryService memory,
                                    Clock clock, MeterRegistry meters) {
        this.orchestrator = orchestrator;
        this.memory = memory;
        this.clock = clock;
        this.meters = meters;
    }

    public ConversationOutcome execute(AuthenticatedUser principal, String message, String conversationId) {
        boolean isNew = (conversationId == null || conversationId.isBlank());

        // Fail-closed load: a missing/foreign id → 404, Redis-down on an EXISTING id → 503 (propagates).
        // A new conversation is minted in-memory and never touches Redis here.
        ConversationMemory memoryState = memory.startOrLoad(principal, conversationId);

        String history = memory.renderContext(memoryState);
        AgentResult result = orchestrator.run(principal, message, history);

        List<MemoryMessage> turn = buildTurn(memoryState, message, result);
        try {
            memory.append(principal, memoryState, turn);
            meters.counter("agent.conversation", "memoryStatus", MemoryStatus.ACTIVE.name()).increment();
            return new ConversationOutcome(result, memoryState.conversationId(), MemoryStatus.ACTIVE);
        } catch (MemoryUnavailableException e) {
            // Best-effort persistence: the turn already executed. Degrade rather than fail after effects.
            log.warn("agent.conversation append unavailable (best-effort) new={} executionId={}",
                    isNew, result.executionId());
            meters.counter("agent.conversation", "memoryStatus", MemoryStatus.UNAVAILABLE.name()).increment();
            String outId = isNew ? null : memoryState.conversationId();
            return new ConversationOutcome(result, outId, MemoryStatus.UNAVAILABLE);
        }
    }

    public void delete(AuthenticatedUser principal, String conversationId) {
        memory.delete(principal, conversationId);
    }

    /** USER message + bounded TOOL summaries (already capped by ObservationSerializer) + ASSISTANT final. */
    private List<MemoryMessage> buildTurn(ConversationMemory memoryState, String message, AgentResult result) {
        Instant now = clock.instant();
        int seq = memoryState.nextSequence();
        List<MemoryMessage> turn = new ArrayList<>();
        turn.add(MemoryMessage.user(message, seq++, now));
        for (AgentObservation o : result.observations()) {
            String summary = o.success()
                    ? o.resultSummary()
                    : (o.errorCode() == null ? o.resultSummary() : o.errorCode() + ": " + o.resultSummary());
            turn.add(MemoryMessage.tool(o.tool(), summary, seq++, now));
        }
        if (result.finalResponse() != null && !result.finalResponse().isBlank()) {
            turn.add(MemoryMessage.assistant(result.finalResponse(), seq++, now));
        }
        return turn;
    }
}
