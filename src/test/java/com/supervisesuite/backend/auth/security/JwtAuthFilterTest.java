package com.supervisesuite.backend.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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
    webEnvironment = WebEnvironment.MOCK,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthFilterTest {

    private static final String AUTHENTICATED_URL  = "/api/test/authenticated";
    private static final String SUPERVISOR_ONLY_URL = "/api/test/supervisor-only";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    // -------------------------------------------------------------------------
    // No / invalid token
    // -------------------------------------------------------------------------

    @Test
    void noToken_onProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidToken_onProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie("invalid.token.here")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void randomBase64BearerToken_onProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie(
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.AAAAAAAAAAAAAAAAAAAAAA"
        )))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_onProtectedEndpoint_returns401() throws Exception {
        // Build a JwtService instance that issues tokens expiring 1 second in the past
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(jwtProperties.getSecret());
        shortLived.setAccessTokenExpirySeconds(-1L);  // negative ⇒ already expired at issuance

        JwtService expiredService = new JwtService(shortLived);
        String expiredToken = expiredService.generateAccessToken(fakeUser(Roles.STUDENT));

        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie(expiredToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithDifferentSecret_returns401() throws Exception {
        JwtProperties wrongSecret = new JwtProperties();
        wrongSecret.setSecret(
            "ZGlmZmVyZW50LXNlY3JldC13aGljaC1pcy1sb25nLWVub3VnaC1mb3ItdGVzdGluZy1wdXJwb3Nlcw=="
        );
        wrongSecret.setAccessTokenExpirySeconds(jwtProperties.getAccessTokenExpirySeconds());

        String tamperedToken = new JwtService(wrongSecret).generateAccessToken(fakeUser(Roles.STUDENT));

        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie(tamperedToken)))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Valid tokens — authenticated endpoint
    // -------------------------------------------------------------------------

    @Test
    void validStudentToken_onAuthenticatedEndpoint_returns200() throws Exception {
        String token = jwtService.generateAccessToken(fakeUser(Roles.STUDENT));
        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie(token)))
            .andExpect(status().isOk());
    }

    @Test
    void validSupervisorToken_onAuthenticatedEndpoint_returns200() throws Exception {
        String token = jwtService.generateAccessToken(fakeUser(Roles.SUPERVISOR));
        mockMvc.perform(get(AUTHENTICATED_URL).cookie(accessCookie(token)))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Role enforcement — supervisor-only endpoint
    // -------------------------------------------------------------------------

    @Test
    void validStudentToken_onSupervisorEndpoint_returns403() throws Exception {
        String token = jwtService.generateAccessToken(fakeUser(Roles.STUDENT));
        mockMvc.perform(get(SUPERVISOR_ONLY_URL).cookie(accessCookie(token)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void validSupervisorToken_onSupervisorEndpoint_returns200() throws Exception {
        String token = jwtService.generateAccessToken(fakeUser(Roles.SUPERVISOR));
        mockMvc.perform(get(SUPERVISOR_ONLY_URL).cookie(accessCookie(token)))
            .andExpect(status().isOk());
    }

    @Test
    void noToken_onSupervisorEndpoint_returns401() throws Exception {
        mockMvc.perform(get(SUPERVISOR_ONLY_URL))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Cookie accessCookie(String token) {
        return new Cookie(CookieService.ACCESS_TOKEN_COOKIE, token);
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
