package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;
import com.supervisesuite.backend.auth.dto.SupervisorRegisterRequest;

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

    /**
     * Registers a new supervisor account.
     * Validates email uniqueness, hashes the password with BCrypt,
     * assigns the SUPERVISOR role, and persists the user.
     * registrationNumber is not applicable for supervisors and must not be set.
     *
     * @param request the validated supervisor registration payload
     * @return a RegisterResponse containing the new supervisor's public profile
     * @throws ConflictException if the email is already in use
     */
    RegisterResponse registerSupervisor(SupervisorRegisterRequest request);

    /**
     * Authenticates a user and issues a short-lived access token and a long-lived refresh token.
     *
     * <p>Deliberately returns a generic error on bad credentials to prevent user enumeration —
     * callers cannot distinguish between an unknown email and a wrong password.
     *
     * @param request the validated login payload containing email and password
     * @return a {@link LoginResponse} with the access token, refresh token, and user profile
     * @throws com.supervisesuite.backend.common.error.UnauthorizedException
     *         if the credentials are invalid or the account has no password set
     */
    LoginResponse login(LoginRequest request);
}
