package com.prince.agentic.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.memory.exception.ConversationNotFoundException;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Real-Redis integration for {@link RedisConversationMemoryService} (Testcontainers redis:7-alpine via
 * {@link AbstractPostgresIntegrationTest}). Exercises create/load/append round-trip, storage trimming,
 * sliding TTL, actual expiration, delete, and cross-user isolation against a live server — not a mock.
 */
class RedisConversationMemoryIT extends AbstractPostgresIntegrationTest {

    private static final AuthenticatedUser USER_A =
            new AuthenticatedUser(101L, "a@example.com", Set.of("ROLE_USER"));
    private static final AuthenticatedUser USER_B =
            new AuthenticatedUser(202L, "b@example.com", Set.of("ROLE_USER"));

    @Autowired private RedisConversationMemoryService service;   // wired with production properties
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;

    private RedisConversationMemoryService serviceWith(MemoryProperties props) {
        return new RedisConversationMemoryService(redis, mapper, props,
                Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test
    void appendThenLoad_roundTripsMessages() {
        ConversationMemory created = service.startOrLoad(USER_A, null);
        service.append(USER_A, created, List.of(
                MemoryMessage.user("show my high priority tasks", 0, Clock.systemUTC().instant()),
                MemoryMessage.assistant("You have 2.", 1, Clock.systemUTC().instant())));

        ConversationMemory loaded = service.startOrLoad(USER_A, created.conversationId());
        assertThat(loaded.messages()).hasSize(2);
        assertThat(loaded.messages().get(0).content()).isEqualTo("show my high priority tasks");
        assertThat(loaded.ownerUserId()).isEqualTo(101L);
    }

    @Test
    void keyNamespace_isUserScoped() {
        ConversationMemory created = service.startOrLoad(USER_A, null);
        service.append(USER_A, created, List.of(
                MemoryMessage.user("hi", 0, Clock.systemUTC().instant())));
        String key = "conv:101:" + created.conversationId();
        assertThat(redis.hasKey(key)).isTrue();
    }

    @Test
    void append_trimsToStorageBound() {
        RedisConversationMemoryService small = serviceWith(new MemoryProperties(3600, 2, 12_000, 12, 6_000));
        ConversationMemory created = small.startOrLoad(USER_A, null);
        ConversationMemory updated = small.append(USER_A, created, List.of(
                MemoryMessage.user("a", 0, Clock.systemUTC().instant()),
                MemoryMessage.assistant("b", 1, Clock.systemUTC().instant()),
                MemoryMessage.user("c", 2, Clock.systemUTC().instant())));
        assertThat(updated.messages()).hasSize(2);

        ConversationMemory reloaded = small.startOrLoad(USER_A, created.conversationId());
        assertThat(reloaded.messages()).hasSize(2);
        assertThat(reloaded.messages().get(1).content()).isEqualTo("c");
    }

    @Test
    void append_setsSlidingTtl() {
        RedisConversationMemoryService ttlService =
                serviceWith(new MemoryProperties(3600, 50, 12_000, 12, 6_000));
        ConversationMemory created = ttlService.startOrLoad(USER_A, null);
        ttlService.append(USER_A, created, List.of(
                MemoryMessage.user("hi", 0, Clock.systemUTC().instant())));

        Long ttl = redis.getExpire("conv:101:" + created.conversationId(), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(3600);
    }

    @Test
    void expiredConversation_isNotFound() {
        RedisConversationMemoryService shortTtl =
                serviceWith(new MemoryProperties(1, 50, 12_000, 12, 6_000)); // 1-second TTL
        ConversationMemory created = shortTtl.startOrLoad(USER_A, null);
        shortTtl.append(USER_A, created, List.of(
                MemoryMessage.user("ephemeral", 0, Clock.systemUTC().instant())));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThatThrownBy(() -> shortTtl.startOrLoad(USER_A, created.conversationId()))
                        .isInstanceOf(ConversationNotFoundException.class));
    }

    @Test
    void delete_removesConversation() {
        ConversationMemory created = service.startOrLoad(USER_A, null);
        service.append(USER_A, created, List.of(
                MemoryMessage.user("hi", 0, Clock.systemUTC().instant())));
        service.delete(USER_A, created.conversationId());

        assertThatThrownBy(() -> service.startOrLoad(USER_A, created.conversationId()))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void delete_missing_throwsNotFound() {
        assertThatThrownBy(() -> service.delete(USER_A, "does-not-exist"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void crossUser_cannotLoadAnotherUsersConversation() {
        ConversationMemory aConv = service.startOrLoad(USER_A, null);
        service.append(USER_A, aConv, List.of(
                MemoryMessage.user("a-secret", 0, Clock.systemUTC().instant())));

        // User B presents A's conversationId → masked 404 (B's key namespace has no such key).
        assertThatThrownBy(() -> service.startOrLoad(USER_B, aConv.conversationId()))
                .isInstanceOf(ConversationNotFoundException.class);
        // And B deleting A's id must not remove A's conversation.
        assertThatThrownBy(() -> service.delete(USER_B, aConv.conversationId()))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThat(service.startOrLoad(USER_A, aConv.conversationId()).messages()).hasSize(1);
    }
}
