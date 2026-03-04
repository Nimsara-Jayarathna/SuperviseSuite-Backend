package com.supervisesuite.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.users.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>No Spring context is loaded — {@link JwtProperties} is manually instantiated
 * so tests remain fast and independent of the application environment.
 */
class JwtServiceTest {

    /**
     * Base64-encoded 58-byte secret (≥ 32 bytes required for HS256).
     * Same value used as the {@code JWT_SECRET} property in integration tests.
     */
    private static final String TEST_SECRET =
        "dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ==";

    /** A different valid secret used to produce tokens that must be rejected by {@code jwtService}. */
    private static final String DIFFERENT_SECRET =
        "ZGlmZmVyZW50LXNlY3JldC13aGljaC1pcy1sb25nLWVub3VnaC1mb3ItdGVzdGluZy1wdXJwb3Nlcw==";

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setAccessTokenExpirySeconds(900);
        jwtService = new JwtService(props);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("amal@university.ac.lk");
        testUser.setRole(Roles.STUDENT);
    }

    // -------------------------------------------------------------------------
    // Happy path — generation and extraction
    // -------------------------------------------------------------------------

    @Test
    void generateAccessToken_extractSubject_returnsCorrectUserId() {
        String token = jwtService.generateAccessToken(testUser);

        Optional<String> subject = jwtService.extractSubject(token);

        assertThat(subject).isPresent();
        assertThat(subject.get()).isEqualTo(testUser.getId().toString());
    }

    @Test
    void generateAccessToken_extractRole_returnsCorrectRole() {
        String token = jwtService.generateAccessToken(testUser);

        Optional<String> role = jwtService.extractRole(token);

        assertThat(role).isPresent();
        assertThat(role.get()).isEqualTo(Roles.STUDENT);
    }

    @Test
    void generateAccessToken_supervisorUser_extractRole_returnsSupervisorRole() {
        testUser.setRole(Roles.SUPERVISOR);
        String token = jwtService.generateAccessToken(testUser);

        Optional<String> role = jwtService.extractRole(token);

        assertThat(role).isPresent();
        assertThat(role.get()).isEqualTo(Roles.SUPERVISOR);
    }

    // -------------------------------------------------------------------------
    // Tampered / wrong-secret tokens — must be rejected
    // -------------------------------------------------------------------------

    @Test
    void extractSubject_tokenSignedWithDifferentSecret_returnsEmpty() {
        JwtService otherService = serviceWithSecret(DIFFERENT_SECRET);
        String foreignToken = otherService.generateAccessToken(testUser);

        assertThat(jwtService.extractSubject(foreignToken)).isEmpty();
    }

    @Test
    void extractRole_tokenSignedWithDifferentSecret_returnsEmpty() {
        JwtService otherService = serviceWithSecret(DIFFERENT_SECRET);
        String foreignToken = otherService.generateAccessToken(testUser);

        assertThat(jwtService.extractRole(foreignToken)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Expired tokens — must be rejected
    // -------------------------------------------------------------------------

    @Test
    void extractSubject_expiredToken_returnsEmpty() {
        // Generate a token that is already expired at the moment of creation (-1 s).
        JwtService expiredService = serviceWithExpiry(-1);
        String expiredToken = expiredService.generateAccessToken(testUser);

        assertThat(jwtService.extractSubject(expiredToken)).isEmpty();
    }

    @Test
    void extractRole_expiredToken_returnsEmpty() {
        JwtService expiredService = serviceWithExpiry(-1);
        String expiredToken = expiredService.generateAccessToken(testUser);

        assertThat(jwtService.extractRole(expiredToken)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Malformed input
    // -------------------------------------------------------------------------

    @Test
    void extractSubject_malformedToken_returnsEmpty() {
        assertThat(jwtService.extractSubject("not.a.jwt")).isEmpty();
    }

    @Test
    void extractRole_malformedToken_returnsEmpty() {
        assertThat(jwtService.extractRole("not.a.jwt")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JwtService serviceWithSecret(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setAccessTokenExpirySeconds(900);
        return new JwtService(props);
    }

    private JwtService serviceWithExpiry(long expirySeconds) {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setAccessTokenExpirySeconds(expirySeconds);
        return new JwtService(props);
    }
}
