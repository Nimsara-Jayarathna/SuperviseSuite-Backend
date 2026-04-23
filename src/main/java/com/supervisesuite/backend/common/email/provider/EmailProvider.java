package com.supervisesuite.backend.common.email.provider;

/**
 * Low-level interface for physical email delivery.
 * 
 * <p>Follows the Interface Segregation and Dependency Inversion principles
 * from SOLID, allowing the system to switch between different email providers
 * (e.g., Brevo, AWS SES, Mailgun) without affecting business logic.
 */
public interface EmailProvider {

    /**
     * Sends a transactional email.
     *
     * @param to          The recipient's email address.
     * @param subject     The email subject line.
     * @param htmlContent The final processed HTML content of the email.
     */
    void send(String to, String subject, String htmlContent);
}
