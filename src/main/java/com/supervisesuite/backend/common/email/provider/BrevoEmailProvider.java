package com.supervisesuite.backend.common.email.provider;

import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brevo-specific implementation of {@link EmailProvider}.
 *
 * <p>Uses the official Brevo Java SDK to send transactional emails.
 */
@Slf4j
@Component
public class BrevoEmailProvider implements EmailProvider {

    private final TransactionalEmailsApi apiInstance;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailProvider(
        TransactionalEmailsApi apiInstance,
        @Value("${app.brevo.sender-email}") String senderEmail,
        @Value("${app.brevo.sender-name}") String senderName
    ) {
        this.apiInstance = apiInstance;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    public void send(String to, String subject, String htmlContent) {
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
        sendSmtpEmail.setSubject(subject);
        sendSmtpEmail.setHtmlContent(htmlContent);

        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(senderEmail);
        sender.setName(senderName);
        sendSmtpEmail.setSender(sender);

        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(to);
        sendSmtpEmail.setTo(Collections.singletonList(recipient));

        try {
            apiInstance.sendTransacEmail(sendSmtpEmail);
        } catch (brevo.ApiException e) {
            log.error("Brevo API Error for {}: Status Code: {}, Response Body: {}", 
                to, e.getCode(), e.getResponseBody());
            throw new ServiceUnavailableException(
                "Email service is currently unavailable. Please try again later.",
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error sending email to: {}. Error: {}", to, e.getMessage(), e);
            throw new ServiceUnavailableException(
                "Email service is currently unavailable. Please try again later.",
                e
            );
        }
    }
}
