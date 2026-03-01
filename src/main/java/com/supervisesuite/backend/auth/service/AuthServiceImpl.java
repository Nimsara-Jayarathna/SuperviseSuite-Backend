package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
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

    AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists.");
        }

        if (userRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new ConflictException("An account with this registration number already exists.");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setRegistrationNumber(request.getRegistrationNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Roles.STUDENT);
        user.setCreatedAt(Instant.now());

        // NOTE: isEmailVerified is not yet in the schema (missing from V2__auth_schema.sql).
        // Add a V4 migration to introduce the column, then set it here.

        User saved = userRepository.save(user);

        return new RegisterResponse(
            saved.getId(),
            saved.getEmail(),
            saved.getFirstName(),
            saved.getLastName(),
            saved.getRegistrationNumber(),
            saved.getRole()
        );
    }
}
