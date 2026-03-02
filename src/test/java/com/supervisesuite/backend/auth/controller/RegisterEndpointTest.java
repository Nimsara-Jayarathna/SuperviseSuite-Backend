package com.supervisesuite.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.dto.RegisterRequest;
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
 * Integration tests for {@code POST /api/auth/register}.
 *
 * <p>Loads the full Spring application context against a real PostgreSQL instance
 * provided by Testcontainers. Flyway runs all migrations before the tests execute,
 * so the schema matches production exactly.
 *
 * <p>Each test starts with a clean {@code users} table, enforced by {@link #cleanUp()}.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@Import(TestcontainersConfiguration.class)
class RegisterEndpointTest {

    private static final String REGISTER_URL = "/api/auth/register";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void register_validRequest_returns201() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            REGISTER_URL, validRequest(), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void register_validRequest_responseBodyHasCorrectFields() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            REGISTER_URL, validRequest(), Map.class
        );

        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("message")).isEqualTo("Registration successful.");

        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("email")).isEqualTo("amal.perera@university.ac.lk");
        assertThat(data.get("firstName")).isEqualTo("Amal");
        assertThat(data.get("lastName")).isEqualTo("Perera");
        assertThat(data.get("registrationNumber")).isEqualTo("CS/2021/001");
        assertThat(data.get("role")).isEqualTo(Roles.STUDENT);
    }

    @Test
    void register_validRequest_responseBodyDoesNotExposePasswordHash() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            REGISTER_URL, validRequest(), Map.class
        );

        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data).doesNotContainKey("passwordHash");
        assertThat(data).doesNotContainKey("password");
    }

    @Test
    void register_validRequest_persistsUserWithHashedPassword() {
        RegisterRequest request = validRequest();
        restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        User saved = userRepository.findByEmail(request.getEmail()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotNull();
        assertThat(saved.getPasswordHash()).isNotEqualTo(request.getPassword());
        assertThat(passwordEncoder.matches(request.getPassword(), saved.getPasswordHash())).isTrue();
    }

    @Test
    void register_validRequest_persistsUserWithStudentRole() {
        RegisterRequest request = validRequest();
        restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        User saved = userRepository.findByEmail(request.getEmail()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Duplicate conflicts
    // -------------------------------------------------------------------------

    @Test
    void register_duplicateEmail_returns409Conflict() {
        persistUser("amal.perera@university.ac.lk", "CS/2021/001");

        RegisterRequest request = validRequest(); // same email, same registration number
        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CONFLICT");
        assertThat(response.getBody().get("message").toString()).containsIgnoringCase("email");
    }

    @Test
    void register_duplicateRegistrationNumber_returns409Conflict() {
        persistUser("other.user@university.ac.lk", "CS/2021/001"); // same reg number, different email

        RegisterRequest request = validRequest(); // email: amal.perera@..., regNumber: CS/2021/001
        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CONFLICT");
        assertThat(response.getBody().get("message").toString()).containsIgnoringCase("registration number");
    }

    // -------------------------------------------------------------------------
    // Validation errors
    // -------------------------------------------------------------------------

    @Test
    void register_emptyBody_returns400WithValidationDetails() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            REGISTER_URL, Map.of(), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<?>) response.getBody().get("details");
        assertThat(details).isNotEmpty();
    }

    @Test
    void register_invalidEmailFormat_returns400WithEmailDetail() {
        RegisterRequest request = validRequest();
        request.setEmail("not-an-email");

        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "email".equals(d.get("field")));
    }

    @Test
    void register_weakPassword_returns400WithPasswordDetail() {
        RegisterRequest request = validRequest();
        request.setPassword("weak"); // too short, no uppercase, no digit, no special char

        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");

        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "password".equals(d.get("field")));
    }

    @Test
    void register_blankFirstName_returns400WithFirstNameDetail() {
        RegisterRequest request = validRequest();
        request.setFirstName("  ");

        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "firstName".equals(d.get("field")));
    }

    @Test
    void register_blankRegistrationNumber_returns400WithRegistrationNumberDetail() {
        RegisterRequest request = validRequest();
        request.setRegistrationNumber("");

        ResponseEntity<Map> response = restTemplate.postForEntity(REGISTER_URL, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var details = (java.util.List<Map<?, ?>>) response.getBody().get("details");
        assertThat(details).anyMatch(d -> "registrationNumber".equals(d.get("field")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a valid {@link RegisterRequest} that satisfies all constraints.
     */
    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Amal");
        request.setLastName("Perera");
        request.setEmail("amal.perera@university.ac.lk");
        request.setRegistrationNumber("CS/2021/001");
        request.setPassword("Secure@123");
        return request;
    }

    /**
     * Saves a minimal {@link User} directly to the repository to set up conflict scenarios.
     *
     * @param email              the email to reserve
     * @param registrationNumber the registration number to reserve
     */
    private void persistUser(String email, String registrationNumber) {
        User user = new User();
        user.setEmail(email);
        user.setRegistrationNumber(registrationNumber);
        user.setPasswordHash(passwordEncoder.encode("Secure@123"));
        user.setRole(Roles.STUDENT);
        user.setFirstName("Existing");
        user.setLastName("User");
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
    }
}
