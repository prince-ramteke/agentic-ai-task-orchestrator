package com.prince.agentic.agent;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeadlineCancellationTokenTest {

    @Test
    void notCancelled_beforeDeadline() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DeadlineCancellationToken t = new DeadlineCancellationToken(clock, now.plusSeconds(60));
        assertThat(t.isCancelled()).isFalse();
    }

    @Test
    void cancelled_afterDeadline() {
        Instant start = Instant.parse("2026-08-21T00:00:00Z");
        Clock clock = Clock.fixed(start.plusSeconds(61), ZoneOffset.UTC);
        DeadlineCancellationToken t = new DeadlineCancellationToken(clock, start.plusSeconds(60));
        assertThat(t.isCancelled()).isTrue();
    }

    @Test
    void explicitCancel_flipsImmediately() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        DeadlineCancellationToken t = new DeadlineCancellationToken(Clock.fixed(now, ZoneOffset.UTC), now.plusSeconds(60));
        t.cancel();
        assertThat(t.isCancelled()).isTrue();
    }
}
