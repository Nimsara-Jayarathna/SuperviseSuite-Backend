package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.LoginRequest;
import com.supervisesuite.backend.auth.dto.LoginResponse;

/**
 * Contract for authentication operations.
 *
 * <p>Implementations handle user login.
 * Business rule enforcement (duplicate checks, password policy) is the
 * responsibility of the implementing class.
 */
public interface AuthService {

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
