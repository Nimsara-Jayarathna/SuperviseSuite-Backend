package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.users.entity.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link RefreshTokenValidator}.
 *
 * <p>Validation steps:
 * <ol>
 *   <li>SHA-256 hash the incoming raw token — matching the algorithm used by
 *       {@link RefreshTokenServiceImpl} when the token was issued.</li>
 *   <li>Look up the hash in {@link RefreshTokenRepository} — missing → throw.</li>
 *   <li>Check {@code revokedAt} is null — explicitly revoked token → throw.</li>
 *   <li>Check {@code expiresAt} is in the future — expired → throw.</li>
 *   <li>Return the associated {@link User}.</li>
 * </ol>
 *
 * <p>All failure cases throw the same generic {@link UnauthorizedException} with
 * the same message to prevent token enumeration — callers cannot determine which
 * check failed.
 *
 * <p>Package-private — callers depend on {@link RefreshTokenValidator} only.
 */
@Service
class RefreshTokenValidatorImpl implements RefreshTokenValidator {

    private static final String INVALID_TOKEN = "Refresh token is invalid or has expired.";

    private final RefreshTokenRepository refreshTokenRepository;

    RefreshTokenValidatorImpl(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public User validate(String rawToken) {
        String tokenHash = sha256Base64(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
            .findByTokenHashWithUser(tokenHash)
            .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN));

        // Explicitly revoked (logout or rotation)
        if (refreshToken.getRevokedAt() != null) {
            throw new UnauthorizedException(INVALID_TOKEN);
        }

        // Past its TTL
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(INVALID_TOKEN);
        }

        return refreshToken.getUser();
    }

    /**
     * Computes the SHA-256 hash of the raw token string and returns it as a
     * standard Base64-encoded string — identical to the algorithm used in
     * {@link RefreshTokenServiceImpl#issue(User)} when the token was stored.
     */
    private static String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in all JVMs — should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
