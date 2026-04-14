package com.supervisesuite.backend.common.scheduler;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UnifiedSystemSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedSystemSyncScheduler.class);

    private final List<SystemSyncProvider> syncProviders;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public UnifiedSystemSyncScheduler(List<SystemSyncProvider> syncProviders) {
        this.syncProviders = syncProviders;
    }

    @Scheduled(
        cron = "${app.system.sync.cron:0 0 0 * * *}",
        zone = "${app.system.sync.zone:UTC}"
    )
    public void executeAllSyncs() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("Skipping unified heavy system sync cycle because a cycle is already running.");
            return;
        }
        LOGGER.info("Starting unified heavy system sync cycle. Providers to route: {}", syncProviders.size());

        try {
            for (SystemSyncProvider provider : syncProviders) {
                try {
                    LOGGER.info("Executing heavy sync provider: {}", provider.getClass().getSimpleName());
                    provider.executeSync();
                } catch (Exception e) {
                    // Ensure failures in isolated syncs do not derail the entire loop
                    LOGGER.error("Error executing sync inside provider: {}", provider.getClass().getSimpleName(), e);
                }
            }
        } finally {
            running.set(false);
        }
        LOGGER.info("Completed unified heavy system sync cycle.");
    }
}
