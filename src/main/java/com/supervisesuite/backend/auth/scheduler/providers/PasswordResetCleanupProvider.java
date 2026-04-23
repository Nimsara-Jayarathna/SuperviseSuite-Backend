package com.supervisesuite.backend.auth.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.auth.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetCleanupProvider.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetCleanupProvider(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Override
    public void executeCleanup() {
        int deleted = passwordResetService.cleanupExpiredAndUsedTokens();
        if (deleted > 0) {
            LOGGER.info("Password reset token cleanup removed {} token(s).", deleted);
        }
    }
}
