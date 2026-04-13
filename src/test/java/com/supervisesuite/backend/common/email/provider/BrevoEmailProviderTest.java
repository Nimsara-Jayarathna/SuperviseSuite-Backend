package com.supervisesuite.backend.common.email.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import brevo.ApiException;
import brevoApi.TransactionalEmailsApi;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrevoEmailProviderTest {

    @Mock
    private TransactionalEmailsApi transactionalEmailsApi;

    private BrevoEmailProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BrevoEmailProvider(transactionalEmailsApi, "noreply@supervisesuite.com", "SuperviseSuite");
    }

    @Test
    void send_dispatchesMessageToBrevoApi() throws Exception {
        provider.send("user@example.com", "Subject", "<html>body</html>");

        verify(transactionalEmailsApi).sendTransacEmail(any());
    }

    @Test
    void send_wrapsBrevoApiExceptionAsServiceUnavailable() throws Exception {
        ApiException apiException = new ApiException("brevo error");
        when(transactionalEmailsApi.sendTransacEmail(any())).thenThrow(apiException);

        assertThatThrownBy(() -> provider.send("user@example.com", "Subject", "<html>body</html>"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Email service is currently unavailable");
    }

    @Test
    void send_wrapsUnexpectedExceptionAsServiceUnavailable() throws Exception {
        when(transactionalEmailsApi.sendTransacEmail(any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> provider.send("user@example.com", "Subject", "<html>body</html>"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Email service is currently unavailable");
    }
}
