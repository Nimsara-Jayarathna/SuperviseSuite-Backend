package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.AuthTestBase;
import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for {@code POST /api/auth/login}.
 *
 * <p>Loads the full Spring application context against a real PostgreSQL instance
 * provided by Testcontainers. Flyway runs all migrations before the tests execute,
 * so the schema matches production exactly.
 *
 * <p>Each test starts with clean {@code users} and {@code refresh_tokens} tables,
 * enforced by {@link #cleanUp()}.
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
class LoginEndpointTest extends AuthTestBase {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String TEST_EMAIL = "amal.perera@university.ac.lk";
    private static final String TEST_PASSWORD = "Secure@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        safeCleanup();
        persistUser(TEST_EMAIL, TEST_PASSWORD, Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk());
    }

    @Test
    void login_validCredentials_responseBodyHasCorrectShape() throws Exception {
        MvcResult result = performLogin(validRequest());
        Map<?, ?> body = body(result);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("message")).isEqualTo("Login successful.");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        // Tokens must NOT be in the body — they are delivered as httpOnly cookies
        assertThat(data).doesNotContainKey("accessToken");
        assertThat(data).doesNotContainKey("refreshToken");
        // User profile is present in the body
        assertThat(data.get("user")).isNotNull();
    }

    @Test
    void login_validCredentials_setsAccessTokenCookie() throws Exception {
        MvcResult result = performLogin(validRequest());
        java.util.List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h -> h.startsWith("ss_access_token="));
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith("ss_access_token=") && h.contains("HttpOnly")
        );
    }

    @Test
    void login_validCredentials_setsRefreshTokenCookie() throws Exception {
        MvcResult result = performLogin(validRequest());
        java.util.List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anyMatch(h -> h.startsWith("ss_refresh_token="));
        assertThat(setCookieHeaders).anyMatch(h ->
            h.startsWith("ss_refresh_token=") && h.contains("HttpOnly")
        );
    }

    @Test
    void login_validCredentials_userInfoIsCorrect() throws Exception {
        MvcResult result = performLogin(validRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(result).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user.get("id")).isNotNull();
        assertThat(user.get("email")).isEqualTo(TEST_EMAIL);
        assertThat(user.get("role")).isEqualTo(Roles.STUDENT);
        assertThat(user.get("firstName")).isNotNull();
        assertThat(user.get("lastName")).isNotNull();
        // emailVerified is not part of the response — no email verification flow exists
        assertThat(user).doesNotContainKey("emailVerified");
    }

    @Test
    void login_validCredentials_doesNotExposePasswordHash() throws Exception {
        MvcResult result = performLogin(validRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body(result).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user).doesNotContainKey("passwordHash");
        assertThat(user).doesNotContainKey("password");
    }

    @Test
    void login_validCredentials_persistsRefreshTokenInDatabase() throws Exception {
        performLogin(validRequest());

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void login_emailIsCaseInsensitive() throws Exception {
        // Register with lowercase; login with mixed case
        LoginRequest request = new LoginRequest();
        request.setEmail("Amal.Perera@University.AC.LK");
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Invalid credentials — must return 401 with generic message (no enumeration)
    // -------------------------------------------------------------------------

    @Test
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("WrongPassword@1");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(result.getResponse().getContentType()).isNotNull();
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType())
            .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();

        Map<?, ?> body = body(result);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(error(body).get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message")).isEqualTo("Invalid email or password.");
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@university.ac.lk");
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(result.getResponse().getContentType()).isNotNull();
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType())
            .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();

        Map<?, ?> body = body(result);
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(error(body).get("code")).isEqualTo("UNAUTHORIZED");
        // Same message as wrong password — prevents user enumeration
        assertThat(body.get("message")).isEqualTo("Invalid email or password.");
    }

    @Test
    void login_wrongPassword_doesNotPersistRefreshToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("WrongPassword@1");

        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Validation errors
    // -------------------------------------------------------------------------

    @Test
    void login_missingEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andReturn();

        Map<?, ?> body = body(result);
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) error(body).get("details");
        assertThat(details).anyMatch(d -> "email".equals(d.get("field")));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andReturn();

        Map<?, ?> body = body(result);
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) error(body).get("details");
        assertThat(details).anyMatch(d -> "email".equals(d.get("field")));
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andReturn();

        Map<?, ?> body = body(result);
        assertThat(error(body).get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) error(body).get("details");
        assertThat(details).anyMatch(d -> "password".equals(d.get("field")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MvcResult performLogin(LoginRequest request) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private Map<?, ?> body(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private LoginRequest validRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        return request;
    }

    private void persistUser(String email, String rawPassword, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setFirstName("Amal");
        user.setLastName("Perera");
        user.setRegistrationNumber("CS/2021/001");
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(Map<?, ?> body) {
        return (Map<String, Object>) body.get("error");
    }
}
