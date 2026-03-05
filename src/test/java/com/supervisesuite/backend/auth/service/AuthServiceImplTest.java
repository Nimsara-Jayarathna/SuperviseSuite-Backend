package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from the database and password encoder.
 * No Spring context is loaded — these tests are fast and focused on business logic only.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setFirstName("Amal");
        validRequest.setLastName("Perera");
        validRequest.setEmail("amal.perera@university.ac.lk");
        validRequest.setRegistrationNumber("CS/2021/001");
        validRequest.setPassword("Secure@123");
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void registerStudent_validRequest_returnsResponseWithCorrectFields() {
        // arrange
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail(validRequest.getEmail());
        savedUser.setFirstName(validRequest.getFirstName());
        savedUser.setLastName(validRequest.getLastName());
        savedUser.setRegistrationNumber(validRequest.getRegistrationNumber());
        savedUser.setRole(Roles.STUDENT);
        savedUser.setPasswordHash("$2a$10$hashedPassword");

        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(validRequest.getRegistrationNumber())).thenReturn(false);
        when(passwordEncoder.encode(validRequest.getPassword())).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // act
        RegisterResponse response = authService.registerStudent(validRequest);

        // assert
        assertThat(response.getEmail()).isEqualTo(validRequest.getEmail());
        assertThat(response.getFirstName()).isEqualTo(validRequest.getFirstName());
        assertThat(response.getLastName()).isEqualTo(validRequest.getLastName());
        assertThat(response.getRegistrationNumber()).isEqualTo(validRequest.getRegistrationNumber());
        assertThat(response.getRole()).isEqualTo(Roles.STUDENT);
        assertThat(response.getId()).isNotNull();
    }

    @Test
    void registerStudent_validRequest_savesUserWithHashedPasswordNotPlainText() {
        // arrange
        String plainPassword = validRequest.getPassword();
        String expectedHash = "$2a$10$hashedValue";

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(passwordEncoder.encode(plainPassword)).thenReturn(expectedHash);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // act
        authService.registerStudent(validRequest);

        // assert: the User passed to save() must have the hash, not the plain-text password
        verify(userRepository).save(argThat(user ->
            expectedHash.equals(user.getPasswordHash()) &&
            !plainPassword.equals(user.getPasswordHash())
        ));
    }

    @Test
    void registerStudent_validRequest_savesUserWithStudentRole() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerStudent(validRequest);

        verify(userRepository).save(argThat(user -> Roles.STUDENT.equals(user.getRole())));
    }

    @Test
    void registerStudent_validRequest_savesUserWithCreatedAtSet() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerStudent(validRequest);

        verify(userRepository).save(argThat(user -> user.getCreatedAt() != null));
    }

    // -------------------------------------------------------------------------
    // Input normalization
    // -------------------------------------------------------------------------

    @Test
    void registerStudent_lowercaseRegistrationNumber_storedAsUppercase() {
        // Input has leading/trailing whitespace and lowercase letters.
        validRequest.setRegistrationNumber("  it24100400  ");
        String expectedNormalized = "IT24100400";

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(expectedNormalized)).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerStudent(validRequest);

        // The normalized (uppercase, trimmed) value must be passed to both the
        // duplicate check and the entity setter.
        verify(userRepository).existsByRegistrationNumber(expectedNormalized);
        verify(userRepository).save(argThat(user -> expectedNormalized.equals(user.getRegistrationNumber())));
    }

    // -------------------------------------------------------------------------
    // Duplicate email
    // -------------------------------------------------------------------------

    @Test
    void registerStudent_duplicateEmail_throwsConflictException() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(validRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("email");
    }

    @Test
    void registerStudent_duplicateEmail_doesNotSaveUser() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(validRequest))
            .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Duplicate registration number
    // -------------------------------------------------------------------------

    @Test
    void registerStudent_duplicateRegistrationNumber_throwsConflictException() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(validRequest.getRegistrationNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(validRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("registration number");
    }

    @Test
    void registerStudent_duplicateRegistrationNumber_doesNotSaveUser() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByRegistrationNumber(validRequest.getRegistrationNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(validRequest))
            .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }
}
