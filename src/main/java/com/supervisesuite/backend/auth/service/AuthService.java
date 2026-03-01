package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.RegisterRequest;
import com.supervisesuite.backend.auth.dto.RegisterResponse;

public interface AuthService {

    /**
     * Registers a new STUDENT account.
     * Throws DomainException(CONFLICT) if email or registration number already exists.
     */
    RegisterResponse registerStudent(RegisterRequest request);
}
