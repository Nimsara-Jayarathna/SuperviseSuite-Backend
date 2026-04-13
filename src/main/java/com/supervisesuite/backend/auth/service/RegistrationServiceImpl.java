package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.RegisterCompleteRequest;
import com.supervisesuite.backend.auth.dto.RegisterVerifyResponse;
import com.supervisesuite.backend.auth.entity.EmailOtp;
import com.supervisesuite.backend.auth.entity.RegistrationSession;
import com.supervisesuite.backend.auth.repository.EmailOtpRepository;
import com.supervisesuite.backend.auth.repository.RegistrationSessionRepository;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.email.service.EmailService;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.config.RegistrationProperties;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
class RegistrationServiceImpl implements RegistrationService {

    private static final String TOKEN_PREFIX = "token_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final RegistrationSessionRepository registrationSessionRepository;
    private final RegistrationProperties registrationProperties;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;

    RegistrationServiceImpl(
        UserRepository userRepository,
        EmailOtpRepository emailOtpRepository,
        RegistrationSessionRepository registrationSessionRepository,
        RegistrationProperties registrationProperties,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        TokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.registrationSessionRepository = registrationSessionRepository;
        this.registrationProperties = registrationProperties;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public void initRegistration(String email) {
        String normalizedEmail = NormalizationUtils.normalizeEmail(email);
        if (!registrationProperties.isEmailAllowed(normalizedEmail)) {
            throw new ValidationException("Email domain not permitted for registration.", List.of());
        }
        if (!registrationProperties.isStudentEmailPrefixAllowed(normalizedEmail)) {
            throw new ValidationException(
                "email",
                "Invalid IT number format. Use ITXXXXXXXX."
            );
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists.");
        }

        String rawOtp = String.format("%06d", SECURE_RANDOM.nextInt(900000) + 100000);
        Instant now = Instant.now();

        EmailOtp emailOtp = new EmailOtp();
        emailOtp.setEmail(normalizedEmail);
        emailOtp.setOtpHash(sha256Base64(rawOtp));
        emailOtp.setExpiresAt(now.plusSeconds(registrationProperties.getOtpExpirySeconds()));
        emailOtp.setCreatedAt(now);
        emailOtpRepository.save(emailOtp);

        emailService.sendOtpEmail(normalizedEmail, rawOtp);
    }

    @Override
    @Transactional
    public RegisterVerifyResponse verifyOtp(String email, String otp) {
        String normalizedEmail = NormalizationUtils.normalizeEmail(email);
        String otpHash = sha256Base64(otp);
        Instant now = Instant.now();

        EmailOtp emailOtp = emailOtpRepository
            .findTopByEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(normalizedEmail, now)
            .orElseThrow(() -> new ValidationException("Invalid or expired OTP.", List.of()));

        if (!emailOtp.getOtpHash().equals(otpHash)) {
            throw new ValidationException("Invalid or expired OTP.", List.of());
        }

        emailOtp.setUsedAt(now);
        emailOtpRepository.save(emailOtp);

        String inferredRole = registrationProperties.inferRole(normalizedEmail);
        String rawRegistrationToken = generateRawToken();
        String tokenHash = sha256Base64(rawRegistrationToken);

        RegistrationSession session = new RegistrationSession();
        session.setTokenHash(tokenHash);
        session.setEmail(normalizedEmail);
        session.setRole(inferredRole);
        session.setExpiresAt(now.plusSeconds(registrationProperties.getSessionExpirySeconds()));
        session.setCreatedAt(now);
        registrationSessionRepository.save(session);

        String resolvedRole = session.getRole();
        return new RegisterVerifyResponse(
            TOKEN_PREFIX + rawRegistrationToken,
            resolvedRole == null,
            resolvedRole
        );
    }

    @Override
    @Transactional
    public LoginResponse completeRegistration(RegisterCompleteRequest request) {
        String submittedToken = request.getRegistrationToken();
        String rawToken = submittedToken.startsWith(TOKEN_PREFIX)
            ? submittedToken.substring(TOKEN_PREFIX.length())
            : submittedToken;

        RegistrationSession session = registrationSessionRepository
            .findByTokenHash(sha256Base64(rawToken))
            .orElseThrow(() -> new ValidationException("Invalid or expired registration token.", List.of()));

        Instant now = Instant.now();
        if (session.getUsedAt() != null) {
            throw new ValidationException("Registration token already used.", List.of());
        }
        if (session.getExpiresAt().isBefore(now)) {
            throw new ValidationException("Registration token has expired.", List.of());
        }

        String effectiveRole = resolveEffectiveRole(session, request);
        if (userRepository.existsByEmail(session.getEmail())) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = new User();
        user.setFirstName(request.getFname());
        user.setLastName(request.getLname());
        user.setEmail(session.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(effectiveRole);
        user.setCreatedAt(now);

        if (Roles.STUDENT.equals(effectiveRole)) {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new ValidationException("registrationNumber", "Registration number is required.");
            }

            String normalizedRegistrationNumber = NormalizationUtils.normalizeRegistrationNumber(request.getName());

            if (registrationProperties.isEffectiveStudentEmailPrefixRestrictionEnabled()) {
                if (!registrationProperties.isStudentIdentifierAllowed(normalizedRegistrationNumber)) {
                    throw new ValidationException("registrationNumber", "Invalid IT number format. Use ITXXXXXXXX.");
                }

                String expectedRegistrationNumber = extractEmailLocalPart(session.getEmail());
                if (expectedRegistrationNumber == null
                    || !expectedRegistrationNumber.equalsIgnoreCase(normalizedRegistrationNumber)) {
                    throw new ValidationException(
                        "registrationNumber",
                        "Registration number must match student email ID."
                    );
                }
            }

            if (userRepository.existsByRegistrationNumber(normalizedRegistrationNumber)) {
                throw new ConflictException("An account with this registration number already exists.");
            }
            user.setRegistrationNumber(normalizedRegistrationNumber);
        }

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Email or registration number already exists.");
        }

        session.setUsedAt(now);
        registrationSessionRepository.save(session);

        String accessToken = tokenService.generateAccessToken(savedUser);
        String refreshToken = refreshTokenService.issue(savedUser);

        try {
            emailService.sendRegistrationSuccessEmail(savedUser.getEmail(), savedUser.getFirstName());
        } catch (Exception ex) {
            log.error("Failed to send registration success email to {}", savedUser.getEmail(), ex);
        }

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            savedUser.getRole()
        );
        return new LoginResponse(accessToken, refreshToken, userInfo);
    }

    private static String resolveEffectiveRole(RegistrationSession session, RegisterCompleteRequest request) {
        if (session.getRole() != null && !session.getRole().isBlank()) {
            return session.getRole();
        }
        String requestedRole = request.getRole();
        if (requestedRole == null || requestedRole.isBlank()) {
            throw new ValidationException("Role is required.", List.of());
        }
        String normalizedRole = requestedRole.trim().toUpperCase();
        if (!Roles.STUDENT.equals(normalizedRole) && !Roles.SUPERVISOR.equals(normalizedRole)) {
            throw new ValidationException("Role must be STUDENT or SUPERVISOR.", List.of());
        }
        return normalizedRole;
    }

    private static String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String extractEmailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return null;
        }
        return NormalizationUtils.normalizeRegistrationNumber(email.substring(0, atIndex));
    }

    private static String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
