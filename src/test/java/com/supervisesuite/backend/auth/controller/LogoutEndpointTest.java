package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.AuthTestBase;
import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.security.CookieService;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    webEnvironment = WebEnvironment.MOCK,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogoutEndpointTest extends AuthTestBase {

    private static final String LOGOUT_URL = "/api/auth/logout";

    @Autowired private MockMvc mockMvc;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User savedUser;

    @BeforeEach
    void setUp() {
        safeCleanup();
        savedUser = persistUser("logout.test@university.ac.lk", "Secure@123");
    }

    // -------------------------------------------------------------------------
    // Status code — always 204
    // -------------------------------------------------------------------------

    @Test
    void logout_withValidRefreshCookie_returns204() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_withNoRefreshCookie_returns204() throws Exception {
        mockMvc.perform(post(LOGOUT_URL))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_withUnknownRefreshCookie_returns204() throws Exception {
        // A well-formed but unrecognised token should not cause an error
        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie("this-is-not-a-known-token")))
            .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------------------
    // Token revocation
    // -------------------------------------------------------------------------

    @Test
    void logout_withValidRefreshCookie_revokesToken() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isNoContent());

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getRevokedAt()).isNotNull();
    }

    @Test
    void logout_withNoRefreshCookie_leavesTokensUntouched() throws Exception {
        refreshTokenService.issue(savedUser);

        mockMvc.perform(post(LOGOUT_URL))
            .andExpect(status().isNoContent());

        // Token was never sent so it must not be revoked
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getRevokedAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // Cookie clearing
    // -------------------------------------------------------------------------

    @Test
    void logout_clearsAccessTokenCookie() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        MvcResult result = mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isNoContent())
            .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
    }

    @Test
    void logout_clearsRefreshTokenCookie() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        MvcResult result = mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isNoContent())
            .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.REFRESH_TOKEN_COOKIE + "=") && h.contains("Max-Age=0")
        );
    }

    @Test
    void logout_withNoRefreshCookie_stillClearsBothCookies() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGOUT_URL))
            .andExpect(status().isNoContent())
            .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
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

    private Cookie refreshCookie(String rawToken) {
        return new Cookie(CookieService.REFRESH_TOKEN_COOKIE, rawToken);
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
