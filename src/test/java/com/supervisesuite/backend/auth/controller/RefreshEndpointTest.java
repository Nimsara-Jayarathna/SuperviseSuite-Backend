package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Integration tests for {@code POST /api/auth/refresh}.
 *
 * <p>Loads the full Spring application context against a real PostgreSQL instance
 * provided by Testcontainers. Each test starts with a clean database state.
 *
 * <p>Refresh tokens are seeded directly via {@link RefreshTokenService#issue(User)}
 * rather than going through the login endpoint, keeping the tests independent of
 * the login flow.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class RefreshEndpointTest {

    private static final String REFRESH_URL = "/api/auth/refresh";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User savedUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        savedUser = persistUser("refresh.test@university.ac.lk", "Secure@123");
    }

    // -------------------------------------------------------------------------
    // Success + token rotation
    // -------------------------------------------------------------------------

    @Test
    void refresh_validCookie_returns200() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Map> response = postWithRefreshCookie(rawToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refresh_validCookie_setsNewAccessTokenCookie() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Map> response = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "=") && h.contains("HttpOnly")
        );
    }

    @Test
    void refresh_validCookie_setsNewRefreshTokenCookie() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Map> response = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.REFRESH_TOKEN_COOKIE + "=") && h.contains("HttpOnly")
        );
    }

    @Test
    void refresh_validCookie_rotatesToken_oldTokenIsRevoked() {
        String rawToken = refreshTokenService.issue(savedUser);

        postWithRefreshCookie(rawToken);

        // After rotation: 2 tokens total — old (revoked) + new (active).
        // Exactly one must have revokedAt set (the old token).
        assertThat(refreshTokenRepository.findAll())
            .filteredOn(t -> t.getRevokedAt() != null)
            .hasSize(1);
    }

    @Test
    void refresh_validCookie_rotatesToken_newTokenExistsInDatabase() {
        String rawToken = refreshTokenService.issue(savedUser);

        postWithRefreshCookie(rawToken);

        // Rotation must have issued a new record (total = 2: old revoked, new active)
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll())
            .anyMatch(t -> t.getRevokedAt() == null);
    }

    @Test
    void refresh_validCookie_responseBodyContainsUserInfo() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Map> response = postWithRefreshCookie(rawToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user.get("email")).isEqualTo(savedUser.getEmail());
        assertThat(user.get("role")).isEqualTo(Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Failure cases
    // -------------------------------------------------------------------------

    @Test
    void refresh_missingCookie_returns401() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            REFRESH_URL, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_invalidToken_returns401() {
        ResponseEntity<Map> response = postWithRefreshCookie("completely-invalid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_alreadyRevokedToken_returns401() {
        String rawToken = refreshTokenService.issue(savedUser);
        refreshTokenService.revoke(rawToken);

        ResponseEntity<Map> response = postWithRefreshCookie(rawToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_reusedToken_returns401() {
        String rawToken = refreshTokenService.issue(savedUser);

        // First use succeeds
        ResponseEntity<Map> first = postWithRefreshCookie(rawToken);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second use must fail — token was rotated (revoked) on first use
        ResponseEntity<Map> second = postWithRefreshCookie(rawToken);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<Map> postWithRefreshCookie(String rawToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieService.REFRESH_TOKEN_COOKIE + "=" + rawToken);
        return restTemplate.exchange(
            REFRESH_URL, HttpMethod.POST, new HttpEntity<>(headers), Map.class
        );
    }

    private User persistUser(String email, String plainPassword) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setRegistrationNumber("IT24000001");
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setRole(Roles.STUDENT);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }
}
