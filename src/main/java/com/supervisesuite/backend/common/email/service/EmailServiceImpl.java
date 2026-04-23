package com.supervisesuite.backend.common.email.service;

import com.supervisesuite.backend.common.email.provider.EmailProvider;
import com.supervisesuite.backend.config.FrontendProperties;
import java.time.Year;
import java.util.HashMap;
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
    private final FrontendProperties frontendProperties;

    @Override
    public void sendRegistrationSuccessEmail(String to, String name) {
        Context context = new Context();
        Map<String, Object> variables = baseTemplateVariables();
        variables.put("name", name);
        context.setVariables(variables);

        String htmlContent = templateEngine.process("email/registration-success", context);
        emailProvider.send(to, "Registration completed - SuperviseSuite", htmlContent);
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        Context context = new Context();
        Map<String, Object> variables = baseTemplateVariables();
        variables.put("otp", otp);
        context.setVariables(variables);

        String htmlContent = templateEngine.process("email/otp-verification", context);
        emailProvider.send(to, "Your SuperviseSuite verification code", htmlContent);
    }

    @Override
    public void sendPasswordChangeAlert(String to, String name) {
        Context context = new Context();
        Map<String, Object> variables = baseTemplateVariables();
        variables.put("name", name);
        context.setVariables(variables);

        String htmlContent = templateEngine.process("email/password-change", context);
        emailProvider.send(to, "Security Alert: Password Changed", htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String resetUrl) {
        Context context = new Context();
        Map<String, Object> variables = baseTemplateVariables();
        variables.put("name", name);
        variables.put("resetUrl", resetUrl);
        context.setVariables(variables);

        String htmlContent = templateEngine.process("email/password-reset", context);
        emailProvider.send(to, "Reset your SuperviseSuite password", htmlContent);
    }

    @Override
    public void sendPasswordResetSuccessEmail(String to, String name) {
        Context context = new Context();
        Map<String, Object> variables = baseTemplateVariables();
        variables.put("name", name);
        context.setVariables(variables);

        String htmlContent = templateEngine.process("email/password-reset-success", context);
        emailProvider.send(to, "Your SuperviseSuite password was reset", htmlContent);
    }

    private Map<String, Object> baseTemplateVariables() {
        String normalizedBaseUrl = normalizeBaseUrl(frontendProperties.getBaseUrl());
        Map<String, Object> variables = new HashMap<>();
        variables.put("currentYear", Year.now().getValue());
        variables.put("frontendBaseUrl", normalizedBaseUrl == null ? "#" : normalizedBaseUrl);
        variables.put("forgotPasswordUrl", normalizedBaseUrl == null ? "#" : normalizedBaseUrl + "/forgot-password");
        return variables;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null) {
            return null;
        }
        String trimmed = rawBaseUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
