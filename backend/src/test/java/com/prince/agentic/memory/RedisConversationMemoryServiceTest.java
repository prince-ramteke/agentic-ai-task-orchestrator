package com.prince.agentic.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.prince.agentic.memory.exception.ConversationNotFoundException;
import com.prince.agentic.memory.exception.MemoryUnavailableException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisConversationMemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final AuthenticatedUser USER =
            new AuthenticatedUser(1L, "u@example.com", Set.of("ROLE_USER"));

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper mapper;
    private MemoryProperties props;
    private RedisConversationMemoryService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        props = new MemoryProperties(3600, 2, 12_000, 12, 6_000); // maxMessages=2 to exercise trimming
        service = new RedisConversationMemoryService(redis, mapper,
                props, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    private String serialized(ConversationMemory memory) {
        try {
            return mapper.writeValueAsString(memory);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- startOrLoad -----------------------------------------------------------

    @Test
    void startOrLoad_nullId_mintsEmpty_withoutTouchingRedis() {
        ConversationMemory memory = service.startOrLoad(USER, null);
        assertThat(memory.conversationId()).isNotBlank();
        assertThat(memory.ownerUserId()).isEqualTo(1L);
        assertThat(memory.messages()).isEmpty();
        assertThat(memory.schemaVersion()).isEqualTo(1);
        verifyNoInteractions(valueOps);
    }

    @Test
    void startOrLoad_existing_ownedByUser_returnsIt() {
        ConversationMemory stored = ConversationMemory.create("c1", 1L, NOW);
        when(valueOps.get("conv:1:c1")).thenReturn(serialized(stored));
        ConversationMemory loaded = service.startOrLoad(USER, "c1");
        assertThat(loaded.conversationId()).isEqualTo("c1");
        assertThat(loaded.ownerUserId()).isEqualTo(1L);
    }

    @Test
    void startOrLoad_missing_throwsNotFound() {
        when(valueOps.get("conv:1:missing")).thenReturn(null);
        assertThatThrownBy(() -> service.startOrLoad(USER, "missing"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void startOrLoad_ownerMismatch_maskedAsNotFound() {
        ConversationMemory foreign = ConversationMemory.create("c1", 999L, NOW);
        when(valueOps.get("conv:1:c1")).thenReturn(serialized(foreign));
        assertThatThrownBy(() -> service.startOrLoad(USER, "c1"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void startOrLoad_malformedBlob_maskedAsNotFound() {
        when(valueOps.get("conv:1:c1")).thenReturn("{ not valid json");
        assertThatThrownBy(() -> service.startOrLoad(USER, "c1"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void startOrLoad_redisDown_throwsUnavailable() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> service.startOrLoad(USER, "c1"))
                .isInstanceOf(MemoryUnavailableException.class);
    }

    // --- append ----------------------------------------------------------------

    @Test
    void append_persistsWithSlidingTtl_andUpdatesLastActivity() {
        ConversationMemory base = ConversationMemory.create("c1", 1L, NOW);
        service.append(USER, base, List.of(MemoryMessage.user("hi", 0, NOW)));
        verify(valueOps).set(eq("conv:1:c1"), anyString(), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void append_trimsToStorageBound() {
        ConversationMemory base = ConversationMemory.create("c1", 1L, NOW);
        // maxMessages=2; append three → only the newest two survive.
        ConversationMemory updated = service.append(USER, base, List.of(
                MemoryMessage.user("a", 0, NOW),
                MemoryMessage.assistant("b", 1, NOW),
                MemoryMessage.user("c", 2, NOW)));
        assertThat(updated.messages()).hasSize(2);
        assertThat(updated.messages().get(1).content()).isEqualTo("c");
    }

    @Test
    void append_redisDown_throwsUnavailable() {
        ConversationMemory base = ConversationMemory.create("c1", 1L, NOW);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        assertThatThrownBy(() -> service.append(USER, base, List.of(MemoryMessage.user("x", 0, NOW))))
                .isInstanceOf(MemoryUnavailableException.class);
    }

    // --- delete ----------------------------------------------------------------

    @Test
    void delete_existing_succeeds() {
        when(redis.delete("conv:1:c1")).thenReturn(true);
        service.delete(USER, "c1");
        verify(redis).delete("conv:1:c1");
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(redis.delete("conv:1:c1")).thenReturn(false);
        assertThatThrownBy(() -> service.delete(USER, "c1"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void delete_redisDown_throwsUnavailable() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> service.delete(USER, "c1"))
                .isInstanceOf(MemoryUnavailableException.class);
    }

    // --- renderContext ---------------------------------------------------------

    @Test
    void renderContext_delegatesToBounds() {
        ConversationMemory memory = new ConversationMemory("c1", 1L, NOW, NOW, 1,
                List.of(MemoryMessage.user("hello", 0, NOW)));
        assertThat(service.renderContext(memory)).isEqualTo("USER: hello");
    }
}
