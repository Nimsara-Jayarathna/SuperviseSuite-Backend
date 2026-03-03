package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;

/**
 * Contract for authentication operations.
 *
 * <p>Implementations handle user registration, login, token refresh, and logout.
 * Business rule enforcement (duplicate checks, password policy) is the
 * responsibility of the implementing class.
 */
public interface AuthService {

    /**
     * Registers a new student account.
     *
     * <p>Validates uniqueness of {@code email} and {@code registrationNumber},
     * hashes the plain-text password with BCrypt, assigns the {@code STUDENT} role,
     * and persists the user.
     *
     * @param request the validated registration payload
     * @return a {@link RegisterResponse} containing the new user's public profile
     * @throws com.supervisesuite.backend.common.error.ConflictException
     *         if the email or registration number is already in use
     */
    RegisterResponse registerStudent(RegisterRequest request);
}
