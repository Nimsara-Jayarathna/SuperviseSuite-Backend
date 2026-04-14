package com.supervisesuite.backend.auth.service;

import com.supervisesuite.backend.auth.dto.LoginResponse;
import com.supervisesuite.backend.auth.dto.RegisterCompleteRequest;
import com.supervisesuite.backend.auth.dto.RegisterVerifyResponse;

public interface RegistrationService {

    void initRegistration(String email);

    RegisterVerifyResponse verifyOtp(String email, String otp);

    LoginResponse completeRegistration(RegisterCompleteRequest request);

    void cleanupExpiredSessionsAndOtps();
}
