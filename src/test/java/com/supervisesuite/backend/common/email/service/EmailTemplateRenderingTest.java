package com.supervisesuite.backend.common.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateRenderingTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    void otpTemplate_rendersOtpProminently() {
        Context context = new Context();
        context.setVariable("otp", "654321");
        context.setVariable("currentYear", 2026);
        context.setVariable("frontendBaseUrl", "https://app.supervisesuite.xyz");
        context.setVariable("forgotPasswordUrl", "https://app.supervisesuite.xyz/forgot-password");

        String html = templateEngine.process("email/otp-verification", context);

        assertThat(html).contains("Your verification code");
        assertThat(html).contains("654321");
        assertThat(html).contains("letter-spacing:6px");
    }

    @Test
    void passwordResetTemplate_rendersResetCtaLink() {
        Context context = new Context();
        context.setVariable("name", "Nimal");
        context.setVariable("resetUrl", "https://app.supervisesuite.xyz/reset?token=abc");
        context.setVariable("currentYear", 2026);
        context.setVariable("frontendBaseUrl", "https://app.supervisesuite.xyz");
        context.setVariable("forgotPasswordUrl", "https://app.supervisesuite.xyz/forgot-password");

        String html = templateEngine.process("email/password-reset", context);

        assertThat(html).contains("Reset your password");
        assertThat(html).contains("Reset Password");
        assertThat(html).contains("https://app.supervisesuite.xyz/reset?token=abc");
    }

    @Test
    void securityTemplates_renderSecureAccountAction() {
        Context context = new Context();
        context.setVariable("name", "Nimal");
        context.setVariable("currentYear", 2026);
        context.setVariable("frontendBaseUrl", "https://app.supervisesuite.xyz");
        context.setVariable("forgotPasswordUrl", "https://app.supervisesuite.xyz/forgot-password");

        String changeHtml = templateEngine.process("email/password-change", context);
        String successHtml = templateEngine.process("email/password-reset-success", context);

        assertThat(changeHtml).contains("Secure My Account");
        assertThat(changeHtml).contains("https://app.supervisesuite.xyz/forgot-password");
        assertThat(successHtml).contains("Secure My Account");
        assertThat(successHtml).contains("https://app.supervisesuite.xyz/forgot-password");
    }
}
