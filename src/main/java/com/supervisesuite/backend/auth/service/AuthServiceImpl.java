package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.auth.entity.RefreshToken;
import com.supervisesuite.backend.auth.repository.RefreshTokenRepository;
import com.supervisesuite.backend.auth.security.JwtService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.config.JwtProperties;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AuthService}.
 *
 * <p>Package-private — callers depend on the {@link AuthService} interface only.
 */
@Service
class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;

    AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenRepository refreshTokenRepository,
        JwtProperties jwtProperties,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.jwtService = jwtService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Check email uniqueness — throws {@link com.supervisesuite.backend.common.error.ConflictException} on duplicate.</li>
     *   <li>Check registration number uniqueness — throws {@link com.supervisesuite.backend.common.error.ConflictException} on duplicate.</li>
     *   <li>Hash the plain-text password with BCrypt via {@link PasswordEncoder}.</li>
     *   <li>Build and persist a {@link com.supervisesuite.backend.users.entity.User} with role {@code STUDENT}.</li>
     *   <li>Return a {@link RegisterResponse} with the saved user's public fields.</li>
     * </ol>
     */
    @Override
    @Transactional
    public RegisterResponse registerStudent(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedRegistrationNumber = NormalizationUtils.normalizeRegistrationNumber(request.getRegistrationNumber());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists.");
        }

        if (userRepository.existsByRegistrationNumber(normalizedRegistrationNumber)) {
            throw new ConflictException("An account with this registration number already exists.");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(normalizedEmail);
        user.setRegistrationNumber(normalizedRegistrationNumber);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Roles.STUDENT);
        user.setCreatedAt(Instant.now());

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Guards against a race condition where two concurrent registrations pass
            // the pre-checks above but one loses the DB uniqueness constraint race.
            throw new ConflictException("Email or registration number already exists.");
        }

        return new RegisterResponse(
            saved.getId(),
            saved.getEmail(),
            saved.getFirstName(),
            saved.getLastName(),
            saved.getRegistrationNumber(),
            saved.getRole()
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Normalize email (trim + lowercase).</li>
     *   <li>Load user by email — throws {@link UnauthorizedException} if not found.</li>
     *   <li>Verify {@code passwordHash} is set — throws {@link UnauthorizedException} if null
     *       (pre-seeded supervisors without a password cannot log in until one is set).</li>
     *   <li>Compare submitted password against stored BCrypt hash — throws
     *       {@link UnauthorizedException} if they do not match.</li>
     *   <li>Generate a signed JWT access token via {@link JwtService}.</li>
     *   <li>Issue a refresh token (SecureRandom bytes, SHA-256 hash stored in DB).</li>
     *   <li>Return {@link LoginResponse} with both tokens and the user's public profile.</li>
     * </ol>
     *
     * <p>Security note: steps 2–4 always throw the same generic message to prevent
     * user enumeration — callers cannot determine whether the email or password was wrong.
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Generic message reused for all credential failures — never distinguish email vs password
        final String BAD_CREDENTIALS = "Invalid email or password.";

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));

        // Pre-seeded supervisors may not have a password yet
        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = issueRefreshToken(user);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole()
        );

        return new LoginResponse(accessToken, rawRefreshToken, userInfo);
    }

    /**
     * Generates a cryptographically secure refresh token, persists its SHA-256 hash,
     * and returns the raw token to the caller.
     *
     * <p>The raw token is never stored — only its SHA-256 hash is kept in the database.
     * The caller must return the raw value to the client exactly once.
     *
     * <p>Generation steps:
     * <ol>
     *   <li>Generate 32 random bytes via {@link SecureRandom}.</li>
     *   <li>Encode as Base64url (URL-safe, no padding) — the raw token string.</li>
     *   <li>Compute the SHA-256 hash of the raw token — stored in the DB.</li>
     *   <li>Persist a {@link RefreshToken} with the hash, expiry, and user FK.</li>
     *   <li>Return the raw token string.</li>
     * </ol>
     *
     * @param user the authenticated user to associate the token with
     * @return the raw (unhashed) refresh token string — send to client once, never log
     */
    private String issueRefreshToken(User user) {
        // 1. Generate 32 cryptographically random bytes
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        // 2. Base64url-encode (URL-safe, no padding) — this is the raw token
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 3. SHA-256 hash of the raw token — only this is stored
        String tokenHash;
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes());
            tokenHash = Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in all JVMs — this should never happen
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

        // 5. Return raw token — caller must send this to the client once
        return rawToken;
    }
}
