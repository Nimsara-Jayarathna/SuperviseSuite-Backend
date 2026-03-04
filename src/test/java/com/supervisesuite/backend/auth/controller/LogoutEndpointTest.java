package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
 * Integration tests for {@code POST /api/auth/logout}.
 *
 * <p>Loads the full Spring application context against a real PostgreSQL instance
 * provided by Testcontainers. Each test starts with a clean database state.
 *
 * <p>Key invariant: logout always returns {@code 204} and always clears both
 * auth cookies — regardless of whether a valid refresh token was sent.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class LogoutEndpointTest {

    private static final String LOGOUT_URL = "/api/auth/logout";

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
        savedUser = persistUser("logout.test@university.ac.lk", "Secure@123");
    }

    // -------------------------------------------------------------------------
    // Status code — always 204
    // -------------------------------------------------------------------------

    @Test
    void logout_withValidRefreshCookie_returns204() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Void> response = postWithRefreshCookie(rawToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_withNoRefreshCookie_returns204() {
        ResponseEntity<Void> response = postWithoutCookie();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_withUnknownRefreshCookie_returns204() {
        // A well-formed but unrecognised token should not cause an error
        ResponseEntity<Void> response = postWithRefreshCookie("this-is-not-a-known-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // -------------------------------------------------------------------------
    // Token revocation
    // -------------------------------------------------------------------------

    @Test
    void logout_withValidRefreshCookie_revokesToken() {
        String rawToken = refreshTokenService.issue(savedUser);

        postWithRefreshCookie(rawToken);

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getRevokedAt()).isNotNull();
    }

    @Test
    void logout_withNoRefreshCookie_leavesTokensUntouched() {
        refreshTokenService.issue(savedUser);

        postWithoutCookie();

        // Token was never sent so it must not be revoked
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getRevokedAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // Cookie clearing
    // -------------------------------------------------------------------------

    @Test
    void logout_clearsAccessTokenCookie() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Void> response = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
    }

    @Test
    void logout_clearsRefreshTokenCookie() {
        String rawToken = refreshTokenService.issue(savedUser);

        ResponseEntity<Void> response = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.REFRESH_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
    }

    @Test
    void logout_withNoRefreshCookie_stillClearsBothCookies() {
        ResponseEntity<Void> response = postWithoutCookie();

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.REFRESH_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<Void> postWithRefreshCookie(String rawToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieService.REFRESH_TOKEN_COOKIE + "=" + rawToken);
        return restTemplate.exchange(
            LOGOUT_URL, HttpMethod.POST, new HttpEntity<>(headers), Void.class
        );
    }

    private ResponseEntity<Void> postWithoutCookie() {
        return restTemplate.exchange(
            LOGOUT_URL, HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), Void.class
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
