package com.supervisesuite.backend.config.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiterService {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final Clock clock;
    private final Map<String, CounterWindow> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupEpochMillis = new AtomicLong(0L);

    public InMemoryRateLimiterService() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiterService(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision evaluate(String key, RateLimitPolicy policy) {
        long now = clock.millis();
        long windowMs = policy.windowSeconds() * 1000L;

        if (windowMs <= 0 || policy.maxRequests() <= 0) {
            return new RateLimitDecision(true, policy.maxRequests(), policy.maxRequests(), 0, 0);
        }

        CounterWindow result = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStartEpochMillis >= windowMs) {
                return new CounterWindow(now, 1);
            }
            existing.count = existing.count + 1;
            return existing;
        });

        maybeCleanup(now, windowMs);

        int remaining = Math.max(policy.maxRequests() - result.count, 0);
        if (result.count <= policy.maxRequests()) {
            long resetAfter = Math.max(1L, (windowMs - Math.max(now - result.windowStartEpochMillis, 0L) + 999L) / 1000L);
            return new RateLimitDecision(true, policy.maxRequests(), remaining, 0, resetAfter);
        }

        long retryAfterSeconds = Math.max(1L, (windowMs - Math.max(now - result.windowStartEpochMillis, 0L) + 999L) / 1000L);
        return new RateLimitDecision(false, policy.maxRequests(), 0, retryAfterSeconds, retryAfterSeconds);
    }

    private void maybeCleanup(long now, long windowMs) {
        long previous = lastCleanupEpochMillis.get();
        if (now - previous < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanupEpochMillis.compareAndSet(previous, now)) {
            return;
        }

        long staleAfter = Math.max(windowMs * 2, CLEANUP_INTERVAL_MILLIS);
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStartEpochMillis > staleAfter);
    }

    private static final class CounterWindow {
        private final long windowStartEpochMillis;
        private int count;

        private CounterWindow(long windowStartEpochMillis, int count) {
            this.windowStartEpochMillis = windowStartEpochMillis;
            this.count = count;
        }
    }
}
