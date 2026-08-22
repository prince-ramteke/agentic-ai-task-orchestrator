package com.prince.agentic.agent;

import com.prince.agentic.memory.ConversationMemory;
import com.prince.agentic.memory.MemoryMessage;
import com.prince.agentic.memory.MemoryRole;
import com.prince.agentic.memory.exception.MemoryUnavailableException;
import com.prince.agentic.memory.support.FakeConversationMemoryService;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final AuthenticatedUser USER =
            new AuthenticatedUser(7L, "u@example.com", Set.of("ROLE_USER"));

    private AgentOrchestrator orchestrator;
    private FakeConversationMemoryService memory;
    private AgentConversationService service;

    private AgentResult result(String response, List<AgentObservation> obs) {
        return new AgentResult("exec-1", AgentStatus.COMPLETED, response, 2, 1, 5L, null, obs);
    }

    @BeforeEach
    void setUp() {
        orchestrator = mock(AgentOrchestrator.class);
        memory = new FakeConversationMemoryService(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new AgentConversationService(orchestrator, memory,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void newConversation_mintsId_active_andPersistsUserAndAssistant() {
        when(orchestrator.run(eq(USER), eq("hi"), anyString()))
                .thenReturn(result("hello", List.of()));

        ConversationOutcome outcome = service.execute(USER, "hi", null);

        assertThat(outcome.memoryStatus()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(outcome.conversationId()).isNotBlank();
        assertThat(outcome.result().finalResponse()).isEqualTo("hello");

        ConversationMemory stored = memory.startOrLoad(USER, outcome.conversationId());
        assertThat(stored.messages()).extracting(MemoryMessage::role)
                .containsExactly(MemoryRole.USER, MemoryRole.ASSISTANT);
        assertThat(stored.messages().get(0).content()).isEqualTo("hi");
    }

    @Test
    void existingConversation_passesPriorContextToOrchestrator() {
        ConversationMemory seed = memory.startOrLoad(USER, null);
        memory.append(USER, seed, List.of(
                MemoryMessage.user("show my high priority tasks", 0, NOW),
                MemoryMessage.assistant("You have 2.", 1, NOW)));
        String cid = seed.conversationId();

        ArgumentCaptor<String> history = ArgumentCaptor.forClass(String.class);
        when(orchestrator.run(eq(USER), eq("which is due first?"), history.capture()))
                .thenReturn(result("Task A.", List.of()));

        ConversationOutcome outcome = service.execute(USER, "which is due first?", cid);

        assertThat(history.getValue()).contains("show my high priority tasks");
        assertThat(outcome.conversationId()).isEqualTo(cid);
        assertThat(outcome.memoryStatus()).isEqualTo(MemoryStatus.ACTIVE);
    }

    @Test
    void toolObservations_arePersistedAsBoundedToolMessages() {
        when(orchestrator.run(eq(USER), eq("find tasks"), anyString()))
                .thenReturn(result("done", List.of(
                        new AgentObservation("task.search", true, "found 3 tasks", null))));

        ConversationOutcome outcome = service.execute(USER, "find tasks", null);

        ConversationMemory stored = memory.startOrLoad(USER, outcome.conversationId());
        assertThat(stored.messages()).extracting(MemoryMessage::role)
                .containsExactly(MemoryRole.USER, MemoryRole.TOOL, MemoryRole.ASSISTANT);
        MemoryMessage toolMsg = stored.messages().get(1);
        assertThat(toolMsg.tool()).isEqualTo("task.search");
        assertThat(toolMsg.content()).isEqualTo("found 3 tasks");
    }

    @Test
    void newConversation_appendUnavailable_degradesToStateless() {
        when(orchestrator.run(eq(USER), eq("hi"), anyString()))
                .thenReturn(result("hello", List.of()));
        memory.setAvailable(false); // startOrLoad(new) mints without Redis; append will fail

        ConversationOutcome outcome = service.execute(USER, "hi", null);

        assertThat(outcome.memoryStatus()).isEqualTo(MemoryStatus.UNAVAILABLE);
        assertThat(outcome.conversationId()).isNull();
        assertThat(outcome.result().finalResponse()).isEqualTo("hello"); // turn still returned
    }

    @Test
    void existingConversation_unavailableAtLoad_propagates503() {
        ConversationMemory seed = memory.startOrLoad(USER, null);
        memory.append(USER, seed, List.of(MemoryMessage.user("earlier", 0, NOW)));
        String cid = seed.conversationId();
        memory.setAvailable(false);

        assertThatThrownBy(() -> service.execute(USER, "continue", cid))
                .isInstanceOf(MemoryUnavailableException.class);
        // Fail-closed: the orchestrator is never invoked when an existing conversation can't load.
        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void delete_delegatesToMemory() {
        ConversationMemory seed = memory.startOrLoad(USER, null);
        memory.append(USER, seed, List.of(MemoryMessage.user("x", 0, NOW)));
        service.delete(USER, seed.conversationId());
        // deleting again → not found
        assertThatThrownBy(() -> service.delete(USER, seed.conversationId()))
                .isInstanceOf(com.prince.agentic.memory.exception.ConversationNotFoundException.class);
    }
}
