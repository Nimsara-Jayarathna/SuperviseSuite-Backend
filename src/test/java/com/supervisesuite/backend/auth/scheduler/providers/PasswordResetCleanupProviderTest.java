package com.supervisesuite.backend.auth.scheduler.providers;

import com.supervisesuite.backend.auth.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetCleanupProviderTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetCleanupProvider provider;

    @Test
    void executeCleanup_ShouldInvokeServiceMethod() {
        provider.executeCleanup();
        verify(passwordResetService, times(1)).cleanupExpiredAndUsedTokens();
    }
}
