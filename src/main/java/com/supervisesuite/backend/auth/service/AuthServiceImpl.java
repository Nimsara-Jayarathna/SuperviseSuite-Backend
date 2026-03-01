package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ErrorCode;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public RegisterResponse registerStudent(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DomainException(
                ErrorCode.CONFLICT,
                HttpStatus.CONFLICT,
                "An account with this email already exists."
            );
        }

        if (userRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DomainException(
                ErrorCode.CONFLICT,
                HttpStatus.CONFLICT,
                "An account with this registration number already exists."
            );
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
