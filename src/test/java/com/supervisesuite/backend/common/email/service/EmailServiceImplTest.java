package com.supervisesuite.backend.common.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.supervisesuite.backend.common.email.provider.EmailProvider;
import com.supervisesuite.backend.config.FrontendProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailServiceImplTest {

    private CapturingEmailProvider emailProvider;
    private FrontendProperties frontendProperties;
    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        emailProvider = new CapturingEmailProvider();
        frontendProperties = new FrontendProperties();
        frontendProperties.setBaseUrl("http://localhost:5173/");

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        service = new EmailServiceImpl(emailProvider, templateEngine, frontendProperties);
    }

    @Test
    void sendOtpEmail_rendersOtpTemplateAndExpectedSubject() {
        service.sendOtpEmail("user@example.com", "123456");

        assertThat(emailProvider.to).isEqualTo("user@example.com");
        assertThat(emailProvider.subject).isEqualTo("Your SuperviseSuite verification code");
        assertThat(emailProvider.htmlContent).contains("Your verification code");
        assertThat(emailProvider.htmlContent).contains("123456");
        assertThat(emailProvider.htmlContent).contains("http://localhost:5173");
    }

    @Test
    void sendRegistrationSuccessEmail_rendersSuccessTemplateAndExpectedSubject() {
        service.sendRegistrationSuccessEmail("user@example.com", "Nimal");

        assertThat(emailProvider.subject).isEqualTo("Registration completed - SuperviseSuite");
        assertThat(emailProvider.htmlContent).contains("Registration completed");
        assertThat(emailProvider.htmlContent).contains("Nimal");
        assertThat(emailProvider.htmlContent).contains("http://localhost:5173");
    }

    @Test
    void sendPasswordChangeAlert_rendersTemplateAndExpectedSubject() {
        service.sendPasswordChangeAlert("user@example.com", "Nimal");

        assertThat(emailProvider.subject).isEqualTo("Security Alert: Password Changed");
        assertThat(emailProvider.htmlContent).contains("Security Alert: Password Changed");
        assertThat(emailProvider.htmlContent).contains("Secure My Account");
        assertThat(emailProvider.htmlContent).contains("http://localhost:5173/forgot-password");
    }

    @Test
    void sendPasswordResetEmail_rendersTemplateAndExpectedSubject() {
        service.sendPasswordResetEmail("user@example.com", "Nimal", "https://example.test/reset-token");

        assertThat(emailProvider.subject).isEqualTo("Reset your SuperviseSuite password");
        assertThat(emailProvider.htmlContent).contains("Reset your password");
        assertThat(emailProvider.htmlContent).contains("Reset Password");
        assertThat(emailProvider.htmlContent).contains("https://example.test/reset-token");
    }

    @Test
    void sendPasswordResetSuccessEmail_rendersTemplateAndExpectedSubject() {
        service.sendPasswordResetSuccessEmail("user@example.com", "Nimal");

        assertThat(emailProvider.subject).isEqualTo("Your SuperviseSuite password was reset");
        assertThat(emailProvider.htmlContent).contains("Password reset successful");
        assertThat(emailProvider.htmlContent).contains("Secure My Account");
        assertThat(emailProvider.htmlContent).contains("http://localhost:5173/forgot-password");
    }

    @Test
    void usesFallbackLinksWhenFrontendBaseUrlMissing() {
        frontendProperties.setBaseUrl("  ");

        service.sendRegistrationSuccessEmail("user@example.com", "Nimal");

        assertThat(emailProvider.htmlContent).contains("href=\"#\"");
    }

    private static final class CapturingEmailProvider implements EmailProvider {
        private String to;
        private String subject;
        private String htmlContent;

        @Override
        public void send(String to, String subject, String htmlContent) {
            this.to = to;
            this.subject = subject;
            this.htmlContent = htmlContent;
        }
    }
}
