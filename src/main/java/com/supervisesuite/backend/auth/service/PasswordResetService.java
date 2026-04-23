package com.supervisesuite.backend.auth.service;

public interface PasswordResetService {
    void requestPasswordReset(String email);

    boolean isResetTokenValid(String rawToken);

    void resetPassword(String rawToken, String newPassword);

    int cleanupExpiredAndUsedTokens();
}
