package com.supervisesuite.backend.auth.scheduler.providers;

import com.supervisesuite.backend.auth.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationCleanupProviderTest {

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private RegistrationCleanupProvider provider;

    @Test
    void executeCleanup_ShouldInvokeServiceMethod() {
        provider.executeCleanup();
        verify(registrationService, times(1)).cleanupExpiredSessionsAndOtps();
    }
}
