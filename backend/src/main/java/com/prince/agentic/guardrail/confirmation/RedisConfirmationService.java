package com.prince.agentic.guardrail.confirmation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.guardrail.FingerprintService;
import com.prince.agentic.guardrail.GuardrailProperties;
import com.prince.agentic.guardrail.exception.ConfirmationAlreadyUsedException;
import com.prince.agentic.guardrail.exception.ConfirmationExpiredException;
import com.prince.agentic.guardrail.exception.ConfirmationMismatchException;
import com.prince.agentic.guardrail.exception.ConfirmationNotFoundException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed confirmation store (spec §6.2, §6.3, §30). One application-owned JSON blob per pending
 * action under {@code guard:confirmation:{id}} — a separate namespace from conversation memory
 * ({@code conv:...}). Mirrors {@code RedisConversationMemoryService}: plain JSON string (no Java
 * native/polymorphic serialization), owner-checked, TTL'd.
 *
 * <p><b>Integrity & single-use (spec §6.3):</b>
 * <ol>
 *   <li>peek (non-destructive) → mask a missing/foreign record as {@code CONFIRMATION_NOT_FOUND};</li>
 *   <li>verify the recomputed fingerprint (tamper detection) and clock-checked expiry;</li>
 *   <li>then {@code GETDEL} to atomically consume — a concurrent loser or replay gets null →
 *       {@code CONFIRMATION_ALREADY_USED}/{@code NOT_FOUND}. Execution happens in the caller only after
 *       a non-null consume, so the action runs <b>at most once</b>.</li>
 * </ol>
 * Ownership is checked <em>before</em> any delete, so a foreign id can never consume another user's
 * pending action.
 */
@Service
public class RedisConfirmationService implements ConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(RedisConfirmationService.class);
    private static final String KEY_PREFIX = "guard:confirmation:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final FingerprintService fingerprints;
    private final GuardrailProperties props;
    private final Clock clock;
    private final MeterRegistry meters;

    public RedisConfirmationService(StringRedisTemplate redis, ObjectMapper mapper,
                                    FingerprintService fingerprints, GuardrailProperties props,
                                    Clock clock, MeterRegistry meters) {
        this.redis = redis;
        this.mapper = mapper;
        this.fingerprints = fingerprints;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public PendingConfirmation create(AuthenticatedUser principal, String conversationId,
                                      PendingAction action) {
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        long ttl = props.confirmationTtlSeconds();
        Instant expiresAt = now.plusSeconds(ttl);

        String canonicalArgs = fingerprints.canonicalArguments(action.arguments());
        String fingerprint = fingerprints.fingerprint(
                principal.userId(), conversationId, action.tool(), canonicalArgs, action.riskLevel());

        Confirmation c = new Confirmation(id, principal.userId(), conversationId, action.executionId(),
                action.tool(), action.arguments(), action.riskLevel(), fingerprint,
                now.toEpochMilli(), expiresAt.toEpochMilli());

        redis.opsForValue().set(key(id), serialize(c), Duration.ofSeconds(ttl));
        meters.counter("guardrail.confirmation_required", "riskLevel", action.riskLevel().name())
                .increment();
        log.info("guardrail.confirmation.created id={} tool={} risk={} user={}",
                id, action.tool(), action.riskLevel(), principal.userId());
        return new PendingConfirmation(id, action.tool(), action.riskLevel(),
                summary(action), expiresAt);
    }

    @Override
    public ConfirmedAction confirm(AuthenticatedUser principal, String confirmationId) {
        String key = key(confirmationId);

        // 1) Non-destructive peek + ownership (mask foreign/absent as NOT_FOUND, without deleting).
        String json = redis.opsForValue().get(key);
        if (json == null) {
            throw new ConfirmationNotFoundException();
        }
        Confirmation c = deserialize(json);
        if (c == null || c.ownerUserId() != principal.userId()) {
            throw new ConfirmationNotFoundException();
        }

        // 2) Integrity: recompute the fingerprint over the stored bound fields.
        String recomputed = fingerprints.fingerprint(c.ownerUserId(), c.conversationId(),
                c.toolName(), fingerprints.canonicalArguments(c.arguments()), c.riskLevel());
        if (!recomputed.equals(c.fingerprint())) {
            log.warn("guardrail.confirmation.mismatch id={} user={}", confirmationId, principal.userId());
            throw new ConfirmationMismatchException();
        }

        // 3) Clock-checked expiry (Redis TTL is the backstop).
        if (clock.instant().toEpochMilli() >= c.expiresAtEpochMs()) {
            redis.delete(key);
            meters.counter("guardrail.confirmation_expired").increment();
            throw new ConfirmationExpiredException();
        }

        // 4) Atomic single-use consume. A concurrent loser / replay sees null here.
        String consumed = redis.opsForValue().getAndDelete(key);
        if (consumed == null) {
            throw new ConfirmationAlreadyUsedException();
        }

        meters.counter("guardrail.confirmation_approved", "riskLevel", c.riskLevel().name()).increment();
        log.info("guardrail.confirmation.approved id={} tool={} user={}",
                confirmationId, c.toolName(), principal.userId());
        return new ConfirmedAction(c.executionId(), c.toolName(), c.arguments(), c.riskLevel());
    }

    @Override
    public void cancel(AuthenticatedUser principal, String confirmationId) {
        String key = key(confirmationId);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            throw new ConfirmationNotFoundException();
        }
        Confirmation c = deserialize(json);
        if (c == null || c.ownerUserId() != principal.userId()) {
            throw new ConfirmationNotFoundException();
        }
        redis.delete(key);
        log.info("guardrail.confirmation.cancelled id={} user={}", confirmationId, principal.userId());
    }

    private String summary(PendingAction action) {
        return "Run tool '" + action.tool() + "' (" + action.riskLevel().name() + ").";
    }

    private String serialize(Confirmation c) {
        try {
            return mapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            // Our own record serializes deterministically; a failure here is a programming error.
            throw new IllegalStateException("Failed to serialize confirmation", e);
        }
    }

    private Confirmation deserialize(String json) {
        try {
            return mapper.readValue(json, Confirmation.class);
        } catch (JsonProcessingException e) {
            // Corrupt/legacy blob: treat as absent rather than 500. Never echo the payload.
            log.warn("guardrail.confirmation corrupt blob, masking as not-found");
            return null;
        }
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }
}
