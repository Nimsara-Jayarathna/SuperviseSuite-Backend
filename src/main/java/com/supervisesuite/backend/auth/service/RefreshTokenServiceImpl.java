package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.users.entity.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link RefreshTokenService}.
 *
 * <p>Encapsulates all knowledge about how refresh tokens are generated, hashed,
 * and stored. No other component needs to know about {@link SecureRandom},
 * SHA-256, or the {@link RefreshTokenRepository} — they all stay here.
 *
 * <p>Package-private — callers depend on the {@link RefreshTokenService} interface.
 */
@Service
class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    RefreshTokenServiceImpl(
        RefreshTokenRepository refreshTokenRepository,
        JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generation steps:
     * <ol>
     *   <li>Generate 32 random bytes via {@link SecureRandom}.</li>
     *   <li>Base64url-encode (URL-safe, no padding) — the raw token string.</li>
     *   <li>Compute the SHA-256 hash of the raw token — the value stored in DB.</li>
     *   <li>Persist a {@link RefreshToken} with the hash, user FK, and expiry.</li>
     *   <li>Return the raw token string to the caller.</li>
     * </ol>
     */
    @Override
    public String issue(User user) {
        // 1. Generate 32 cryptographically random bytes
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        // 2. Base64url-encode without padding — the raw token sent to the client
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 3. SHA-256 hash of the raw token — only this is stored
        String tokenHash;
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes());
            tokenHash = Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in all JVMs — should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

        // 4. Persist the hashed token
        Instant now = Instant.now();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(now.plusSeconds(jwtProperties.getRefreshTokenExpirySeconds()));
        refreshToken.setCreatedAt(now);
        refreshTokenRepository.save(refreshToken);

        // 5. Return raw token — caller must send this to the client once, never log it
        return rawToken;
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = sha256Base64(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    /**
     * Computes the SHA-256 hash of the raw token and returns it as standard
     * Base64 — identical to the algorithm used when the token is stored.
     */
    private static String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    @Transactional
    public void cleanupExpiredAndRevokedTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBeforeOrRevokedAtIsNotNull(Instant.now());
    }
}
