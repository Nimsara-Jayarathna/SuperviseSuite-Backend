package com.supervisesuite.backend.config.ratelimit;

public record RateLimitPolicy(String name, int windowSeconds, int maxRequests) {
}
