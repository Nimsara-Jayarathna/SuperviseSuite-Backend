package com.supervisesuite.backend.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterServiceTest {

    @Test
    void evaluate_allowsRequestsWithinLimitAndBlocksOverflow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiterService service = new InMemoryRateLimiterService(clock);
        RateLimitPolicy policy = new RateLimitPolicy("auth-login", 60, 2);

        RateLimitDecision first = service.evaluate("k1", policy);
        RateLimitDecision second = service.evaluate("k1", policy);
        RateLimitDecision third = service.evaluate("k1", policy);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);

        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(0);

        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void evaluate_resetsCounterAfterWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiterService service = new InMemoryRateLimiterService(clock);
        RateLimitPolicy policy = new RateLimitPolicy("auth-refresh", 60, 1);

        RateLimitDecision first = service.evaluate("k2", policy);
        RateLimitDecision blocked = service.evaluate("k2", policy);

        clock.advanceSeconds(61);
        RateLimitDecision afterWindow = service.evaluate("k2", policy);

        assertThat(first.allowed()).isTrue();
        assertThat(blocked.allowed()).isFalse();
        assertThat(afterWindow.allowed()).isTrue();
        assertThat(afterWindow.remaining()).isEqualTo(0);
    }

    @Test
    void evaluate_tracksKeysIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiterService service = new InMemoryRateLimiterService(clock);
        RateLimitPolicy policy = new RateLimitPolicy("authenticated-default", 60, 1);

        RateLimitDecision firstUser = service.evaluate("user:1", policy);
        RateLimitDecision secondUser = service.evaluate("user:2", policy);
        RateLimitDecision firstUserBlocked = service.evaluate("user:1", policy);

        assertThat(firstUser.allowed()).isTrue();
        assertThat(secondUser.allowed()).isTrue();
        assertThat(firstUserBlocked.allowed()).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
