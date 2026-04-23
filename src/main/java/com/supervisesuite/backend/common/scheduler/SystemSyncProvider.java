package com.supervisesuite.backend.common.scheduler;

/**
 * Interface representing a scheduled major network sync task (e.g. GitHub repos, Jira issues).
 * The {@link UnifiedSystemSyncScheduler} will systematically execute implementations of this
 * interface sequentially on a distinct heavy-sync schedule (by default overnight).
 */
public interface SystemSyncProvider {
    /**
     * Executes the internal heavy synchronization logic for this specific module.
     * Implementations should log the batch counts or gracefully handle failures.
     */
    void executeSync();
}
