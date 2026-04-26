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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    webEnvironment = WebEnvironment.MOCK,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshEndpointTest extends AuthTestBase {

    private static final String REFRESH_URL = "/api/auth/refresh";

    @Autowired private MockMvc mockMvc;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private User savedUser;

    @BeforeEach
    void setUp() {
        safeCleanup();
        savedUser = persistUser("refresh.test@university.ac.lk", "Secure@123");
    }

    // -------------------------------------------------------------------------
    // Success + token rotation
    // -------------------------------------------------------------------------

    @Test
    void refresh_validCookie_returns200() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isOk());
    }

    @Test
    void refresh_validCookie_setsNewAccessTokenCookie() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        MvcResult result = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "=") && h.contains("HttpOnly")
        );
    }

    @Test
    void refresh_validCookie_setsNewRefreshTokenCookie() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        MvcResult result = postWithRefreshCookie(rawToken);

        List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith(CookieService.REFRESH_TOKEN_COOKIE + "=") && h.contains("HttpOnly")
        );
    }

    @Test
    void refresh_validCookie_rotatesToken_oldTokenIsRevoked() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        postWithRefreshCookie(rawToken);

        // After rotation: 2 tokens total — old (revoked) + new (active).
        // Exactly one must have revokedAt set (the old token).
        assertThat(refreshTokenRepository.findAll())
            .filteredOn(t -> t.getRevokedAt() != null)
            .hasSize(1);
    }

    @Test
    void refresh_validCookie_rotatesToken_newTokenExistsInDatabase() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        postWithRefreshCookie(rawToken);

        // Rotation must have issued a new record (total = 2: old revoked, new active)
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll())
            .anyMatch(t -> t.getRevokedAt() == null);
    }

    @Test
    void refresh_validCookie_responseBodyContainsUserInfo() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        MvcResult result = postWithRefreshCookie(rawToken);
        Map<?, ?> responseBody = body(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user.get("email")).isEqualTo(savedUser.getEmail());
        assertThat(user.get("role")).isEqualTo(Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Failure cases
    // -------------------------------------------------------------------------

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        MvcResult result = mockMvc.perform(post(REFRESH_URL))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(error(body(result)).get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        MvcResult result = mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie("completely-invalid-token")))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(error(body(result)).get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_alreadyRevokedToken_returns401() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);
        refreshTokenService.revoke(rawToken);

        MvcResult result = mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(error(body(result)).get("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_reusedToken_returns401() throws Exception {
        String rawToken = refreshTokenService.issue(savedUser);

        // First use succeeds
        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isOk());

        // Second use must fail — token was rotated (revoked) on first use
        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_expiredToken_returns401() throws Exception {
        // Insert an expired token directly — RefreshTokenService.issue() always
        // sets a future expiry, so we must bypass it to test this path.
        String rawToken = "manually-crafted-expired-token";
        RefreshToken expired = new RefreshToken();
        expired.setUser(savedUser);
        expired.setTokenHash(sha256Base64(rawToken));
        expired.setExpiresAt(Instant.now().minusSeconds(60)); // 1 minute in the past
        expired.setCreatedAt(Instant.now().minusSeconds(120));
        expired.setRevokedAt(null);
        refreshTokenRepository.save(expired);

        MvcResult result = mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(error(body(result)).get("code")).isEqualTo("UNAUTHORIZED");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MvcResult postWithRefreshCookie(String rawToken) throws Exception {
        return mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(rawToken)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private Cookie refreshCookie(String rawToken) {
        return new Cookie(CookieService.REFRESH_TOKEN_COOKIE, rawToken);
    }

    private Map<?, ?> body(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
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

    /** Mirrors the SHA-256/Base64 hashing used by {@code RefreshTokenServiceImpl}. */
    private static String sha256Base64(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(Map<?, ?> body) {
        return (Map<String, Object>) body.get("error");
    }
}
