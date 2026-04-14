package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.entity.PasswordResetToken;
import com.supervisesuite.backend.auth.repository.PasswordResetTokenRepository;
import com.supervisesuite.backend.common.email.service.EmailService;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.config.PasswordResetProperties;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    private PasswordResetServiceImpl passwordResetService;

    @BeforeEach
    void setUp() {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setBaseUrl("http://localhost:5173");

        PasswordResetProperties passwordResetProperties = new PasswordResetProperties();
        passwordResetProperties.setTokenExpiryMinutes(15);

        passwordResetService = new PasswordResetServiceImpl(
            passwordResetTokenRepository,
            userRepository,
            passwordEncoder,
            refreshTokenService,
            emailService,
            frontendProperties,
            passwordResetProperties
        );
    }

    @Test
    void resetPassword_rejectsSameAsCurrentPassword() {
        String rawToken = "reset-token";
        String newPassword = "same-password";

        User user = new User();
        user.setEmail("student@sliit.lk");
        user.setFirstName("Alex");
        user.setPasswordHash("stored-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);

        when(passwordResetTokenRepository.findActiveByTokenHash(anyString(), any(Instant.class)))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(newPassword, "stored-hash"))
            .thenReturn(true);

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, newPassword))
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException validationException = (ValidationException) ex;
                assertThat(validationException.getDetails()).hasSize(1);
                assertThat(validationException.getDetails().get(0).getField()).isEqualTo("newPassword");
                assertThat(validationException.getDetails().get(0).getIssue())
                    .isEqualTo("New password must be different from current password.");
            });

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(refreshTokenService, never()).revokeAllForUser(any(User.class));
        verify(emailService, never()).sendPasswordResetSuccessEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_updatesPasswordAndInvalidatesSessions() {
        String rawToken = "reset-token";
        String newPassword = "brand-new-password";

        User user = new User();
        user.setEmail("student@sliit.lk");
        user.setFirstName("Alex");
        user.setPasswordHash("stored-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);

        when(passwordResetTokenRepository.findActiveByTokenHash(anyString(), any(Instant.class)))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(newPassword, "stored-hash"))
            .thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("new-hash");

        passwordResetService.resetPassword(rawToken, newPassword);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenService).revokeAllForUser(user);
        verify(emailService).sendPasswordResetSuccessEmail("student@sliit.lk", "Alex");
    }
}
