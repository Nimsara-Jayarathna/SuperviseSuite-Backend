package com.supervisesuite.backend.common.email.service;

import com.supervisesuite.backend.common.email.provider.EmailProvider;
import java.time.Year;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Implementation of {@link EmailService} that uses Thymeleaf for templating
 * and a generic {@link EmailProvider} for delivery.
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailProvider emailProvider;
    private final TemplateEngine templateEngine;

    @Override
    public void sendRegistrationSuccessEmail(String to, String name) {
        Context context = new Context();
        context.setVariables(Map.of(
            "name", name,
            "currentYear", Year.now().getValue()
        ));

        String htmlContent = templateEngine.process("email/registration-success", context);
        emailProvider.send(to, "Registration completed - SuperviseSuite", htmlContent);
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        Context context = new Context();
        context.setVariables(Map.of(
            "otp", otp,
            "currentYear", Year.now().getValue()
        ));

        String htmlContent = templateEngine.process("email/otp-verification", context);
        emailProvider.send(to, "Your SuperviseSuite verification code", htmlContent);
    }

    @Override
    public void sendPasswordChangeAlert(String to, String name) {
        Context context = new Context();
        context.setVariables(Map.of(
            "name", name,
            "currentYear", Year.now().getValue()
        ));

        String htmlContent = templateEngine.process("email/password-change", context);
        emailProvider.send(to, "Security Alert: Password Changed", htmlContent);
    }
}
