package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.dto.RegisterCompleteRequest;
import com.supervisesuite.backend.auth.entity.EmailOtp;
import com.supervisesuite.backend.auth.entity.RegistrationSession;
import com.supervisesuite.backend.auth.repository.EmailOtpRepository;
import com.supervisesuite.backend.auth.repository.RegistrationSessionRepository;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.email.service.EmailService;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.RegistrationProperties;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailOtpRepository emailOtpRepository;

    @Mock
    private RegistrationSessionRepository registrationSessionRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenService tokenService;

    private RegistrationProperties registrationProperties;
    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        registrationProperties = new RegistrationProperties();
        registrationProperties.setDomainRestrictionEnabled(true);
        registrationProperties.setStudentEmailDomain("@my.sliit.lk");
        registrationProperties.setSupervisorEmailDomain("@sliit.lk");
        registrationProperties.setStudentEmailPrefixRestrictionEnabled(true);
        registrationProperties.setStudentEmailPrefixRegex("^IT(1[5-9]|[2-4][0-9]|50)[0-9]{6}$");

        service = new RegistrationServiceImpl(
            userRepository,
            emailOtpRepository,
            registrationSessionRepository,
            registrationProperties,
            emailService,
            passwordEncoder,
            refreshTokenService,
            tokenService
        );
    }

    @Test
    void initRegistration_rejectsInvalidStudentPrefixWhenEnabled() {
        assertThatThrownBy(() -> service.initRegistration("xx24123456@my.sliit.lk"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Validation failed");

        verify(userRepository, never()).existsByEmail(anyString());
        verify(emailOtpRepository, never()).save(any(EmailOtp.class));
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void initRegistration_acceptsValidStudentPrefixWhenEnabled() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.initRegistration("it24123456@my.sliit.lk");

        verify(userRepository).existsByEmail("it24123456@my.sliit.lk");
        verify(emailOtpRepository).save(any(EmailOtp.class));
        verify(emailService).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void initRegistration_bypassesPrefixRestrictionWhenDomainRestrictionDisabled() {
        registrationProperties.setDomainRestrictionEnabled(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.initRegistration("xx14123456@gmail.com");

        verify(userRepository).existsByEmail("xx14123456@gmail.com");
        verify(emailOtpRepository).save(any(EmailOtp.class));
        verify(emailService).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void completeRegistration_requiresStudentRegistrationNumber() {
        when(registrationSessionRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.of(activeSession("it24123456@my.sliit.lk", Roles.STUDENT)));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        RegisterCompleteRequest request = baseCompleteRequest();
        request.setName("  ");

        assertThatThrownBy(() -> service.completeRegistration(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Validation failed");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void completeRegistration_rejectsInvalidStudentRegistrationFormatWhenRestrictionEnabled() {
        when(registrationSessionRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.of(activeSession("it24123456@my.sliit.lk", Roles.STUDENT)));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        RegisterCompleteRequest request = baseCompleteRequest();
        request.setName("IT24DDDDDD");

        assertThatThrownBy(() -> service.completeRegistration(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Validation failed");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void completeRegistration_rejectsMismatchedStudentRegistrationNumberWhenRestrictionEnabled() {
        when(registrationSessionRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.of(activeSession("it24123456@my.sliit.lk", Roles.STUDENT)));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        RegisterCompleteRequest request = baseCompleteRequest();
        request.setName("IT24123457");

        assertThatThrownBy(() -> service.completeRegistration(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Validation failed");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void completeRegistration_bypassesStudentMatchRuleWhenDomainRestrictionDisabled() {
        registrationProperties.setDomainRestrictionEnabled(false);
        when(registrationSessionRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.of(activeSession("it24123456@my.sliit.lk", Roles.STUDENT)));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("pw");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(registrationSessionRepository.save(any(RegistrationSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.generateAccessToken(any(User.class))).thenReturn("access");
        when(refreshTokenService.issue(any(User.class))).thenReturn("refresh");

        RegisterCompleteRequest request = baseCompleteRequest();
        request.setName("IT24111111");

        assertThatCode(() -> service.completeRegistration(request)).doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
        verify(emailService).sendRegistrationSuccessEmail("it24123456@my.sliit.lk", "Nimal");
    }

    private static RegisterCompleteRequest baseCompleteRequest() {
        RegisterCompleteRequest request = new RegisterCompleteRequest();
        request.setRegistrationToken("token_test-token");
        request.setFname("Nimal");
        request.setLname("Perera");
        request.setPassword("Secure@123");
        request.setName("IT24123456");
        return request;
    }

    private static RegistrationSession activeSession(String email, String role) {
        RegistrationSession session = new RegistrationSession();
        session.setTokenHash("hash");
        session.setEmail(email);
        session.setRole(role);
        session.setCreatedAt(Instant.now().minusSeconds(60));
        session.setExpiresAt(Instant.now().plusSeconds(600));
        session.setUsedAt(null);
        return session;
    }
}
