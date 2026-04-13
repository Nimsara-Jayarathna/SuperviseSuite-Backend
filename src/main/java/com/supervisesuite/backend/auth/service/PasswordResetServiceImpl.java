package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.entity.PasswordResetToken;
import com.supervisesuite.backend.auth.repository.PasswordResetTokenRepository;
import com.supervisesuite.backend.common.email.service.EmailService;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.config.PasswordResetProperties;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetServiceImpl implements PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final FrontendProperties frontendProperties;
    private final PasswordResetProperties passwordResetProperties;

    PasswordResetServiceImpl(
        PasswordResetTokenRepository passwordResetTokenRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        EmailService emailService,
        FrontendProperties frontendProperties,
        PasswordResetProperties passwordResetProperties
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
        this.frontendProperties = frontendProperties;
        this.passwordResetProperties = passwordResetProperties;
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = NormalizationUtils.normalizeEmail(email);
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            return;
        }

        User user = maybeUser.get();
        String rawToken = generateRawToken();

        PasswordResetToken token = new PasswordResetToken();
        Instant now = Instant.now();
        token.setUser(user);
        token.setTokenHash(sha256Base64(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(Math.max(1, passwordResetProperties.getTokenExpiryMinutes()) * 60L));
        passwordResetTokenRepository.save(token);

        String resetUrl = buildResetUrl(rawToken);
        emailService.sendPasswordResetEmail(
            user.getEmail(),
            user.getFirstName() == null ? "there" : user.getFirstName(),
            resetUrl
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isResetTokenValid(String rawToken) {
        String tokenHash = sha256Base64(rawToken);
        return passwordResetTokenRepository.findActiveByTokenHash(tokenHash, Instant.now()).isPresent();
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256Base64(rawToken);
        PasswordResetToken token = passwordResetTokenRepository
            .findActiveByTokenHash(tokenHash, Instant.now())
            .orElseThrow(() -> new ValidationException("token", "Reset token is invalid or expired."));

        User user = token.getUser();
        if (user.getPasswordHash() != null && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ValidationException("newPassword", "New password must be different from current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        refreshTokenService.revokeAllForUser(user);
        emailService.sendPasswordResetSuccessEmail(
            user.getEmail(),
            user.getFirstName() == null ? "there" : user.getFirstName()
        );
    }

    @Override
    @Transactional
    public int cleanupExpiredAndUsedTokens() {
        return passwordResetTokenRepository.deleteExpiredOrUsed(Instant.now());
    }

    private static String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String sha256Base64(String value) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private String buildResetUrl(String rawToken) {
        String baseUrl = frontendProperties.getBaseUrl() == null ? "" : frontendProperties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/reset-password?token=" + rawToken;
    }
}
