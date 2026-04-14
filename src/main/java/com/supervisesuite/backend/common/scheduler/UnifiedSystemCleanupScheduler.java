package com.supervisesuite.backend.common.scheduler;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UnifiedSystemCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedSystemCleanupScheduler.class);

    private final List<SystemCleanupProvider> cleanupProviders;

    public UnifiedSystemCleanupScheduler(List<SystemCleanupProvider> cleanupProviders) {
        this.cleanupProviders = cleanupProviders;
    }

    // Default runs every 15 minutes if not specified
    @Scheduled(fixedDelayString = "${app.system.cleanup.fixed-delay-ms:900000}")
    public void executeAllCleanups() {
        LOGGER.debug("Starting unified system cleanup cycle. Providers to run: {}", cleanupProviders.size());

        for (SystemCleanupProvider provider : cleanupProviders) {
            try {
                LOGGER.debug("Executing cleanup provider: {}", provider.getClass().getSimpleName());
                provider.executeCleanup();
            } catch (Exception e) {
                // Catch generic exception so one failing provider doesn't stop the rest
                LOGGER.error("Error executing cleanup in provider: {}", provider.getClass().getSimpleName(), e);
            }
        }
        
        LOGGER.debug("Completed unified system cleanup cycle.");
    }
}
