package com.prince.agentic.guardrail.confirmation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.guardrail.FingerprintService;
import com.prince.agentic.guardrail.GuardrailProperties;
import com.prince.agentic.guardrail.exception.ConfirmationExpiredException;
import com.prince.agentic.guardrail.exception.ConfirmationMismatchException;
import com.prince.agentic.guardrail.exception.ConfirmationNotFoundException;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolRiskLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Logic paths of the confirmation store with a mocked Redis: TTL on create, masked not-found,
 * cross-user masking, clock-checked expiry, and fingerprint-tamper detection. Real single-use /
 * replay / concurrency is proven against a live Redis in {@code RedisConfirmationIT}.
 */
class RedisConfirmationServiceTest {

    private final AuthenticatedUser userA = new AuthenticatedUser(1L, "a@b.com", Set.of("ROLE_USER"));
    private final AuthenticatedUser userB = new AuthenticatedUser(2L, "b@b.com", Set.of("ROLE_USER"));

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private final ObjectMapper mapper = new ObjectMapper();
    private final FingerprintService fingerprints = new FingerprintService(new ObjectMapper());
    private final GuardrailProperties props = new GuardrailProperties(300, 60, 4000);
    private Clock clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
    }

    private RedisConfirmationService service() {
        return new RedisConfirmationService(redis, mapper, fingerprints, props, clock, new SimpleMeterRegistry());
    }

    private PendingAction action() {
        return new PendingAction("task.create", Map.of("title", "review"), ToolRiskLevel.SIDE_EFFECTING);
    }

    @Test
    void create_storesJsonWithConfiguredTtl_andReturnsSafeView() {
        PendingConfirmation pc = service().create(userA, "conv-1", action());
        assertThat(pc.tool()).isEqualTo("task.create");
        assertThat(pc.riskLevel()).isEqualTo(ToolRiskLevel.SIDE_EFFECTING);
        assertThat(pc.confirmationId()).isNotBlank();
        verify(ops).set(eq("guard:confirmation:" + pc.confirmationId()), anyString(),
                eq(Duration.ofSeconds(300)));
    }

    @Test
    void confirm_absent_isMaskedNotFound() {
        when(ops.get(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service().confirm(userA, "missing"))
                .isInstanceOf(ConfirmationNotFoundException.class);
        verify(ops, never()).getAndDelete(anyString());
    }

    @Test
    void confirm_byWrongUser_isMaskedNotFound_withoutConsuming() {
        String json = storedJson(userA, "conv-1", action(), clock);
        when(ops.get(anyString())).thenReturn(json);
        assertThatThrownBy(() -> service().confirm(userB, "id"))
                .isInstanceOf(ConfirmationNotFoundException.class);
        verify(ops, never()).getAndDelete(anyString()); // never deletes another user's confirmation
    }

    @Test
    void confirm_expired_throwsExpired_andDeletes() {
        String json = storedJson(userA, "conv-1", action(), clock);
        when(ops.get(anyString())).thenReturn(json);
        // Advance the clock past the 300s TTL.
        clock = Clock.fixed(Instant.parse("2026-08-22T01:00:00Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> service().confirm(userA, "id"))
                .isInstanceOf(ConfirmationExpiredException.class);
        verify(redis).delete(anyString());
    }

    @Test
    void confirm_tamperedFingerprint_throwsMismatch() {
        // Store a blob whose fingerprint does not match its bound fields.
        Confirmation tampered = new Confirmation("id", 1L, "conv-1", "task.create",
                Map.of("title", "review"), ToolRiskLevel.SIDE_EFFECTING, "deadbeef",
                clock.instant().toEpochMilli(), clock.instant().plusSeconds(300).toEpochMilli());
        when(ops.get(anyString())).thenReturn(toJson(tampered));
        assertThatThrownBy(() -> service().confirm(userA, "id"))
                .isInstanceOf(ConfirmationMismatchException.class);
        verify(ops, never()).getAndDelete(anyString());
    }

    @Test
    void confirm_valid_consumesAndReturnsStoredAction() {
        String json = storedJson(userA, "conv-1", action(), clock);
        when(ops.get(anyString())).thenReturn(json);
        when(ops.getAndDelete(anyString())).thenReturn(json);
        ConfirmedAction confirmed = service().confirm(userA, "id");
        assertThat(confirmed.toolName()).isEqualTo("task.create");
        assertThat(confirmed.arguments()).containsEntry("title", "review");
        verify(ops).getAndDelete(anyString());
    }

    // --- helpers ---

    private String storedJson(AuthenticatedUser owner, String conv, PendingAction a, Clock c) {
        String canonical = fingerprints.canonicalArguments(a.arguments());
        String fp = fingerprints.fingerprint(owner.userId(), conv, a.tool(), canonical, a.riskLevel());
        Confirmation confirmation = new Confirmation("id", owner.userId(), conv, a.tool(),
                a.arguments(), a.riskLevel(), fp,
                c.instant().toEpochMilli(), c.instant().plusSeconds(300).toEpochMilli());
        return toJson(confirmation);
    }

    private String toJson(Confirmation c) {
        try {
            return mapper.writeValueAsString(c);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
