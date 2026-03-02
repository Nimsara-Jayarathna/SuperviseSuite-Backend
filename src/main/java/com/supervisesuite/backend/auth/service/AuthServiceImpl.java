package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.users.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
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
        String normalizedEmail = request.getEmail().trim().toLowerCase();
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
}
