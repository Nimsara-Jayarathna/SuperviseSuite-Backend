package com.supervisesuite.backend.config.ratelimit;

public record RateLimitDecision(
    boolean allowed,
    int limit,
    int remaining,
    long retryAfterSeconds,
    long resetAfterSeconds
) {
}
