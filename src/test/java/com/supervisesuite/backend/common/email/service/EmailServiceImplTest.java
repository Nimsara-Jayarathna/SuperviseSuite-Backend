package com.supervisesuite.backend.common.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.email.provider.EmailProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private EmailProvider emailProvider;

    @Mock
    private TemplateEngine templateEngine;

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl(emailProvider, templateEngine);
    }

    @Test
    void sendOtpEmail_rendersOtpTemplateAndSendsExpectedSubject() {
        when(templateEngine.process(eq("email/otp-verification"), any(Context.class))).thenReturn("<html>otp</html>");

        service.sendOtpEmail("user@example.com", "123456");

        verify(emailProvider).send("user@example.com", "Your SuperviseSuite verification code", "<html>otp</html>");
    }

    @Test
    void sendRegistrationSuccessEmail_rendersSuccessTemplateAndSendsExpectedSubject() {
        when(templateEngine.process(eq("email/registration-success"), any(Context.class))).thenReturn("<html>ok</html>");

        service.sendRegistrationSuccessEmail("user@example.com", "Nimal");

        verify(emailProvider).send("user@example.com", "Registration completed - SuperviseSuite", "<html>ok</html>");
    }

    @Test
    void sendPasswordChangeAlert_rendersPasswordTemplateAndSendsExpectedSubject() {
        when(templateEngine.process(eq("email/password-change"), any(Context.class))).thenReturn("<html>alert</html>");

        service.sendPasswordChangeAlert("user@example.com", "Nimal");

        verify(emailProvider).send("user@example.com", "Security Alert: Password Changed", "<html>alert</html>");
    }
}
