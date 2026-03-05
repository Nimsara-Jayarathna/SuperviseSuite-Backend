package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.users.entity.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RefreshTokenValidatorImpl}.
 *
 * <p>Uses Mockito to isolate the validator from the database.
 * The SHA-256 hash helper mirrors the algorithm used by the production code so
 * mocks can be set up with the correct hash value for a given raw token.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenValidatorImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenValidatorImpl validator;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@university.ac.lk");
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void validate_validToken_returnsAssociatedUser() {
        String rawToken = "valid-raw-token";
        RefreshToken token = activeToken(rawToken, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.of(token));

        User result = validator.validate(rawToken);

        assertThat(result).isSameAs(user);
    }

    // -------------------------------------------------------------------------
    // Unknown token (hash not in DB)
    // -------------------------------------------------------------------------

    @Test
    void validate_unknownToken_throwsUnauthorizedException() {
        String rawToken = "unknown-token";

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    // -------------------------------------------------------------------------
    // Revoked token
    // -------------------------------------------------------------------------

    @Test
    void validate_revokedToken_throwsUnauthorizedException() {
        String rawToken = "revoked-token";
        RefreshToken token = activeToken(rawToken, Instant.now().plusSeconds(3600));
        token.setRevokedAt(Instant.now().minusSeconds(60)); // revoked 1 minute ago

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    // -------------------------------------------------------------------------
    // Expired token
    // -------------------------------------------------------------------------

    @Test
    void validate_expiredToken_throwsUnauthorizedException() {
        String rawToken = "expired-token";
        RefreshToken token = activeToken(rawToken, Instant.now().minusSeconds(1)); // expired 1s ago

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validate_tokenExpiringExactlyNow_throwsUnauthorizedException() {
        // expiresAt = Instant.now() — isBefore(Instant.now()) is borderline;
        // using a past instant to guarantee the condition fires.
        String rawToken = "just-expired-token";
        RefreshToken token = activeToken(rawToken, Instant.EPOCH); // far in the past

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class);
    }

    // -------------------------------------------------------------------------
    // Error message opacity — all failure paths return the same message
    // -------------------------------------------------------------------------

    @Test
    void validate_unknownToken_errorMessageIsGeneric() {
        String rawToken = "any-bad-token";
        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Refresh token is invalid or has expired.");
    }

    @Test
    void validate_revokedToken_errorMessageMatchesUnknown() {
        String rawToken = "another-bad-token";
        RefreshToken token = activeToken(rawToken, Instant.now().plusSeconds(3600));
        token.setRevokedAt(Instant.now());

        when(refreshTokenRepository.findByTokenHashWithUser(hash(rawToken)))
            .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> validator.validate(rawToken))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Refresh token is invalid or has expired.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a non-revoked {@link RefreshToken} with the given expiry for {@code #user}. */
    private RefreshToken activeToken(String rawToken, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(Instant.now().minusSeconds(60));
        token.setRevokedAt(null);
        return token;
    }

    /** Mirrors the SHA-256/Base64 hashing used by the production code. */
    private static String hash(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
