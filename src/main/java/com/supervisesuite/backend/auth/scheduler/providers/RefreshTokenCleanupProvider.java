package com.supervisesuite.backend.auth.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.auth.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenCleanupProvider.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupProvider(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void executeCleanup() {
        refreshTokenService.cleanupExpiredAndRevokedTokens();
        LOGGER.debug("Refresh token cleanup completed.");
    }
}
