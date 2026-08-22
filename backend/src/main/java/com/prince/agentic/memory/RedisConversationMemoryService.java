package com.prince.agentic.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.memory.exception.ConversationNotFoundException;
import com.prince.agentic.memory.exception.MemoryUnavailableException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed conversation memory (spec §2, §3). Stores each conversation as one application-owned
 * JSON blob under {@code conv:{userId}:{conversationId}} with a sliding TTL. Never uses Java native
 * serialization or class-name polymorphic storage; never stores entities, tokens, or security context.
 *
 * <p><b>Ownership</b> is enforced two ways: the key is namespaced by the authenticated user's id, and
 * the stored {@code ownerUserId} is asserted equal to the principal on load. A foreign, guessed, or
 * expired id yields a masked {@link ConversationNotFoundException} (404).
 *
 * <p><b>Failure</b> is fail-closed for reads (load/delete of an existing conversation → 503 via
 * {@link MemoryUnavailableException}) and best-effort for writes (the caller decides whether an
 * append failure degrades a new conversation or surfaces on an existing one).
 */
@Service
public class RedisConversationMemoryService implements ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationMemoryService.class);
    private static final String KEY_PREFIX = "conv:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MemoryProperties props;
    private final Clock clock;
    private final MeterRegistry meters;

    public RedisConversationMemoryService(StringRedisTemplate redis, ObjectMapper mapper,
                                          MemoryProperties props, Clock clock, MeterRegistry meters) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public ConversationMemory startOrLoad(AuthenticatedUser principal, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            String id = UUID.randomUUID().toString();
            return ConversationMemory.create(id, principal.userId(), clock.instant());
        }
        Timer.Sample sample = Timer.start(meters);
        String key = key(principal.userId(), conversationId);
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            meters.counter("memory.unavailable", "op", "load").increment();
            throw new MemoryUnavailableException(e);
        }
        if (json == null) {
            meters.counter("memory.miss").increment();
            throw new ConversationNotFoundException();
        }
        ConversationMemory memory = deserializeOrAbsent(json);
        // Defense-in-depth: the key is already user-scoped, but never trust a stored owner mismatch.
        if (memory.ownerUserId() != principal.userId()) {
            log.warn("memory.load owner mismatch, masking as not-found conversationId={}", conversationId);
            throw new ConversationNotFoundException();
        }
        sample.stop(meters.timer("memory.load"));
        meters.counter("memory.hit").increment();
        return memory;
    }

    @Override
    public ConversationMemory append(AuthenticatedUser principal, ConversationMemory memory,
                                     List<MemoryMessage> newMessages) {
        if (memory.ownerUserId() != principal.userId()) {
            // Should never happen (caller passes a loaded, owned memory); refuse rather than cross users.
            throw new ConversationNotFoundException();
        }
        Timer.Sample sample = Timer.start(meters);

        List<MemoryMessage> combined = new ArrayList<>(memory.messages());
        combined.addAll(newMessages);
        int before = combined.size();
        List<MemoryMessage> trimmed =
                MemoryBounds.trimForStorage(combined, props.maxMessages(), props.maxChars());
        if (trimmed.size() < before) {
            meters.counter("memory.trim").increment();
        }

        ConversationMemory updated = new ConversationMemory(
                memory.conversationId(), memory.ownerUserId(), memory.createdAt(),
                clock.instant(), ConversationMemory.CURRENT_SCHEMA_VERSION, trimmed);

        String key = key(principal.userId(), updated.conversationId());
        String json;
        try {
            json = mapper.writeValueAsString(updated);
        } catch (JsonProcessingException e) {
            // Our own records serialize deterministically; a failure here is a programming error.
            throw new IllegalStateException("Failed to serialize conversation memory", e);
        }
        try {
            redis.opsForValue().set(key, json, Duration.ofSeconds(props.ttlSeconds()));
        } catch (RuntimeException e) {
            meters.counter("memory.unavailable", "op", "append").increment();
            throw new MemoryUnavailableException(e);
        }
        sample.stop(meters.timer("memory.append"));
        return updated;
    }

    @Override
    public void delete(AuthenticatedUser principal, String conversationId) {
        String key = key(principal.userId(), conversationId);
        Boolean existed;
        try {
            existed = redis.delete(key);
        } catch (RuntimeException e) {
            meters.counter("memory.unavailable", "op", "delete").increment();
            throw new MemoryUnavailableException(e);
        }
        if (existed == null || !existed) {
            throw new ConversationNotFoundException();
        }
    }

    @Override
    public String renderContext(ConversationMemory memory) {
        return MemoryBounds.renderContext(memory.messages(),
                props.contextMaxMessages(), props.contextMaxChars());
    }

    private ConversationMemory deserializeOrAbsent(String json) {
        try {
            return mapper.readValue(json, ConversationMemory.class);
        } catch (JsonProcessingException e) {
            // Corrupt/legacy blob: treat as absent rather than 500. Never echo the payload.
            log.warn("memory.load corrupt blob, masking as not-found");
            throw new ConversationNotFoundException();
        }
    }

    private String key(long userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }
}
