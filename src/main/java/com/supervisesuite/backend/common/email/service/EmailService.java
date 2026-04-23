package com.supervisesuite.backend.common.email.service;

/**
 * High-level service for handling business-logic related email operations.
 *
 * <p>This service focuses on user-facing concepts (e.g., registration success email)
 * rather than low-level delivery details.
 */
public interface EmailService {

    /**
     * Sends a registration completion confirmation email to a newly created user.
     *
     * @param to   Recipient email address.
     * @param name Recipient's display name.
     */
    void sendRegistrationSuccessEmail(String to, String name);

    /**
     * Sends a one-time password email for registration verification.
     *
     * @param to Recipient email address.
     * @param otp Six-digit verification code.
     */
    void sendOtpEmail(String to, String otp);

    /**
     * Sends a security alert for a password change.
     *
     * @param to   Recipient email address.
     * @param name Recipient's display name.
     */
    void sendPasswordChangeAlert(String to, String name);

    /**
     * Sends a password reset email containing a single-use reset link.
     *
     * @param to Recipient email address.
     * @param name Recipient display name.
     * @param resetUrl Password reset URL containing the raw token.
     */
    void sendPasswordResetEmail(String to, String name, String resetUrl);

    /**
     * Sends a post-reset notification after a password reset completes.
     *
     * @param to Recipient email address.
     * @param name Recipient display name.
     */
    void sendPasswordResetSuccessEmail(String to, String name);
}
