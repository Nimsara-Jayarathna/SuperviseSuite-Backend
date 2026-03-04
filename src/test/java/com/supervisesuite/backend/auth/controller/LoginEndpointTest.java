package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class LoginEndpointTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String TEST_EMAIL = "amal.perera@university.ac.lk";
    private static final String TEST_PASSWORD = "Secure@123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        persistUser(TEST_EMAIL, TEST_PASSWORD, Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            LOGIN_URL, validRequest(), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_validCredentials_responseBodyHasCorrectShape() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            LOGIN_URL, validRequest(), Map.class
        );

        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("message")).isEqualTo("Login successful.");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("accessToken")).isNotNull().asString().isNotBlank();
        assertThat(data.get("refreshToken")).isNotNull().asString().isNotBlank();
    }

    @Test
    void login_validCredentials_userInfoIsCorrect() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            LOGIN_URL, validRequest(), Map.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user.get("id")).isNotNull();
        assertThat(user.get("email")).isEqualTo(TEST_EMAIL);
        assertThat(user.get("role")).isEqualTo(Roles.STUDENT);
        assertThat(user.get("firstName")).isNotNull();
        assertThat(user.get("lastName")).isNotNull();
        assertThat(user.get("emailVerified")).isEqualTo(true);
    }

    @Test
    void login_validCredentials_doesNotExposePasswordHash() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            LOGIN_URL, validRequest(), Map.class
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");

        assertThat(user).doesNotContainKey("passwordHash");
        assertThat(user).doesNotContainKey("password");
    }

    @Test
    void login_validCredentials_persistsRefreshTokenInDatabase() {
        restTemplate.postForEntity(LOGIN_URL, validRequest(), Map.class);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void login_emailIsCaseInsensitive() {
        // Register with lowercase; login with mixed case
        LoginRequest request = new LoginRequest();
        request.setEmail("Amal.Perera@University.AC.LK");
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------------
    // Invalid credentials — must return 401 with generic message (no enumeration)
    // -------------------------------------------------------------------------

    @Test
    void login_wrongPassword_returns401() {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("WrongPassword@1");

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().get("message")).isEqualTo("Invalid email or password.");
    }

    @Test
    void login_unknownEmail_returns401() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@university.ac.lk");
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("UNAUTHORIZED");
        // Same message as wrong password — prevents user enumeration
        assertThat(response.getBody().get("message")).isEqualTo("Invalid email or password.");
    }

    @Test
    void login_wrongPassword_doesNotPersistRefreshToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("WrongPassword@1");

        restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Validation errors
    // -------------------------------------------------------------------------

    @Test
    void login_missingEmail_returns400() {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "email".equals(d.get("field")));
    }

    @Test
    void login_invalidEmailFormat_returns400() {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "email".equals(d.get("field")));
    }

    @Test
    void login_missingPassword_returns400() {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("");

        ResponseEntity<Map> response = restTemplate.postForEntity(LOGIN_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "password".equals(d.get("field")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
}
