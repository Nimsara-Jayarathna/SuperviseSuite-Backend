package com.supervisesuite.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for {@link JwtAuthFilter} request interception behaviour and
 * the {@code @PreAuthorize} role-enforcement wired by {@code @EnableMethodSecurity}.
 *
 * <p>The filter does <em>not</em> query the database on every request — it reads the
 * {@code role} claim directly from the JWT — so these tests need no {@code users}
 * table rows. Tokens are generated against the test secret via the auto-wired
 * {@link JwtService} and delivered via the {@code ss_access_token} httpOnly cookie.
 *
 * <p>The two helper endpoints exercised here are provided by
 * {@link com.supervisesuite.backend.auth.controller.TestSecurityController}, which
 * lives in test sources and is picked up automatically by {@code @SpringBootTest}.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class JwtAuthFilterTest {

    private static final String AUTHENTICATED_URL  = "/api/test/authenticated";
    private static final String SUPERVISOR_ONLY_URL = "/api/test/supervisor-only";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    // -------------------------------------------------------------------------
    // No / invalid token
    // -------------------------------------------------------------------------

    @Test
    void noToken_onProtectedEndpoint_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(AUTHENTICATED_URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void invalidToken_onProtectedEndpoint_returns401() {
        ResponseEntity<Map> response = get(AUTHENTICATED_URL, "invalid.token.here");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void randomBase64BearerToken_onProtectedEndpoint_returns401() {
        ResponseEntity<Map> response = get(AUTHENTICATED_URL,
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.AAAAAAAAAAAAAAAAAAAAAA");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredToken_onProtectedEndpoint_returns401() {
        // Build a JwtService instance that issues tokens expiring 1 second in the past
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(jwtProperties.getSecret());
        shortLived.setAccessTokenExpirySeconds(-1L);  // negative ⇒ already expired at issuance

        JwtService expiredService = new JwtService(shortLived);
        String expiredToken = expiredService.generateAccessToken(fakeUser(Roles.STUDENT));

        ResponseEntity<Map> response = get(AUTHENTICATED_URL, expiredToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithDifferentSecret_returns401() {
        JwtProperties wrongSecret = new JwtProperties();
        wrongSecret.setSecret(
            "ZGlmZmVyZW50LXNlY3JldC13aGljaC1pcy1sb25nLWVub3VnaC1mb3ItdGVzdGluZy1wdXJwb3Nlcw=="
        );
        wrongSecret.setAccessTokenExpirySeconds(jwtProperties.getAccessTokenExpirySeconds());

        String tamperedToken = new JwtService(wrongSecret).generateAccessToken(fakeUser(Roles.STUDENT));

        ResponseEntity<Map> response = get(AUTHENTICATED_URL, tamperedToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Valid tokens — authenticated endpoint
    // -------------------------------------------------------------------------

    @Test
    void validStudentToken_onAuthenticatedEndpoint_returns200() {
        String token = jwtService.generateAccessToken(fakeUser(Roles.STUDENT));

        ResponseEntity<Map> response = get(AUTHENTICATED_URL, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void validSupervisorToken_onAuthenticatedEndpoint_returns200() {
        String token = jwtService.generateAccessToken(fakeUser(Roles.SUPERVISOR));

        ResponseEntity<Map> response = get(AUTHENTICATED_URL, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------------
    // Role enforcement — supervisor-only endpoint
    // -------------------------------------------------------------------------

    @Test
    void validStudentToken_onSupervisorEndpoint_returns403() {
        String token = jwtService.generateAccessToken(fakeUser(Roles.STUDENT));

        ResponseEntity<Map> response = get(SUPERVISOR_ONLY_URL, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void validSupervisorToken_onSupervisorEndpoint_returns200() {
        String token = jwtService.generateAccessToken(fakeUser(Roles.SUPERVISOR));

        ResponseEntity<Map> response = get(SUPERVISOR_ONLY_URL, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void noToken_onSupervisorEndpoint_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(SUPERVISOR_ONLY_URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Performs a GET request with the token set as an httpOnly-style cookie. */
    private ResponseEntity<Map> get(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieService.ACCESS_TOKEN_COOKIE + "=" + token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    /**
     * Creates a transient {@link User} with a random ID and the given role.
     * The user is <em>not</em> persisted — the filter reads the role from the
     * JWT claim, not from the database.
     */
    private User fakeUser(String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}
