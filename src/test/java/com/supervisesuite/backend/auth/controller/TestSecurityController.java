package com.supervisesuite.backend.auth.controller;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that provides stable endpoints for security integration tests.
 *
 * <p>Lives in {@code src/test/java} and is picked up by {@code @SpringBootTest}'s
 * component scan only during test runs — never deployed to production.
 *
 * <p>Used by {@link JwtAuthFilterTest} to verify 401 (missing/invalid token) and
 * 403 (valid token, wrong role) behavior without coupling tests to real business endpoints
 * that may not exist yet.
 */
@RestController
@RequestMapping("/api/test")
class TestSecurityController {

    /**
     * Requires a valid JWT (any role).
     * No token or invalid token → 401 via {@code authenticationEntryPoint}.
     */
    @GetMapping("/authenticated")
    Map<String, String> authenticated() {
        return Map.of("status", "ok");
    }

    /**
     * Requires a valid JWT with the {@code SUPERVISOR} role.
     * No token or invalid token → 401.
     * Valid token with wrong role (e.g. STUDENT) → 403 via {@code accessDeniedHandler}.
     */
    @GetMapping("/supervisor-only")
    @PreAuthorize("hasRole('SUPERVISOR')")
    Map<String, String> supervisorOnly() {
        return Map.of("status", "ok");
    }
}
