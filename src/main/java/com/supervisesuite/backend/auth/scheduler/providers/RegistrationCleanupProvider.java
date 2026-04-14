package com.supervisesuite.backend.auth.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.auth.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RegistrationCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationCleanupProvider.class);

    private final RegistrationService registrationService;

    public RegistrationCleanupProvider(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Override
    public void executeCleanup() {
        registrationService.cleanupExpiredSessionsAndOtps();
        LOGGER.debug("Registration sessions and OTPs cleanup completed.");
    }
}
