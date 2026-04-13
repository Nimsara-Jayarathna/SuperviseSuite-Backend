package com.supervisesuite.backend.auth.scheduler;

import com.supervisesuite.backend.auth.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetTokenCleanupJob.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetTokenCleanupJob(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(cron = "${app.auth.password-reset.cleanup-cron:0 0 * * * *}")
    public void cleanup() {
        int deleted = passwordResetService.cleanupExpiredAndUsedTokens();
        if (deleted > 0) {
            LOGGER.info("Password reset token cleanup removed {} token(s).", deleted);
        }
    }
}
