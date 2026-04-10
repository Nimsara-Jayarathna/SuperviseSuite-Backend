package com.supervisesuite.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.auth.dto.SupervisorRegisterRequest;
import com.supervisesuite.backend.auth.security.TokenService;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplSupervisorTest {

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

    private SupervisorRegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new SupervisorRegisterRequest();
        request.setFirstName("Nimal");
        request.setLastName("Perera");
        request.setEmail("nimal@sliit.lk");
        request.setPassword("Secure@123");
    }

    @Test
    void registerSupervisor_successPath_returnsSupervisorWithNullRegistrationNumber() {
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("nimal@sliit.lk");
        savedUser.setFirstName("Nimal");
        savedUser.setLastName("Perera");
        savedUser.setRole(Roles.SUPERVISOR);

        when(userRepository.existsByEmail(eq("nimal@sliit.lk"))).thenReturn(false);
        when(passwordEncoder.encode(eq("Secure@123"))).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.registerSupervisor(request);

        assertThat(response.getRole()).isEqualTo(Roles.SUPERVISOR);
        assertThat(response.getRegistrationNumber()).isNull();
    }

    @Test
    void registerSupervisor_duplicateEmail_throwsConflictException() {
        when(userRepository.existsByEmail(eq("nimal@sliit.lk"))).thenReturn(true);

        assertThatThrownBy(() -> authService.registerSupervisor(request))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerSupervisor_roleAssignment_setsSupervisorRoleAndNullRegistrationNumber() {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        when(userRepository.existsByEmail(eq("nimal@sliit.lk"))).thenReturn(false);
        when(passwordEncoder.encode(eq("Secure@123"))).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.registerSupervisor(request);

        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();

        assertThat(captured.getRole()).isEqualTo(Roles.SUPERVISOR);
        assertThat(captured.getRegistrationNumber()).isNull();
    }

    @Test
    void registerSupervisor_neverSetsStudentRole() {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        when(userRepository.existsByEmail(eq("nimal@sliit.lk"))).thenReturn(false);
        when(passwordEncoder.encode(eq("Secure@123"))).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.registerSupervisor(request);

        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();

        assertThat(captured.getRole()).isNotEqualTo(Roles.STUDENT);
    }
}
