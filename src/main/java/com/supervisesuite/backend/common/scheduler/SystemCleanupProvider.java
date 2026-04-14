package com.supervisesuite.backend.common.scheduler;

/**
 * Interface representing a scheduled cleanup task for any system data.
 * The {@link UnifiedSystemCleanupScheduler} will automatically detect all registered
 * implementations across all packages and execute them sequentially according to its configured schedule.
 */
public interface SystemCleanupProvider {
    /**
     * Executes the internal cleanup logic for this specific module.
     * Implementations should log the number of cleaned entities or handle errors internally.
     */
    void executeCleanup();
}
