package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemSyncProvider;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class GitHubRepositorySyncProvider implements SystemSyncProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubRepositorySyncProvider.class);

    private final GitHubProperties gitHubProperties;
    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final GitHubSyncService gitHubSyncService;

    public GitHubRepositorySyncProvider(
        GitHubProperties gitHubProperties,
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        GitHubSyncService gitHubSyncService
    ) {
        this.gitHubProperties = gitHubProperties;
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.gitHubSyncService = gitHubSyncService;
    }

    @Override
    public void executeSync() {
        GitHubProperties.RepositoryRefresh config = gitHubProperties.getJobs().getRepositoryRefresh();
        if (config == null || !config.isEnabled()) {
            return;
        }

        int batchSize = Math.max(1, config.getBatchSize());
        List<ProjectRepositoryLink> candidateLinks = projectRepositoryLinkRepository
            .findByIsEnabledTrueOrderByLastSyncedAtAsc(
                PageRequest.of(0, batchSize)
            );

        if (candidateLinks.isEmpty()) {
            LOGGER.info("GitHub repository sync provider found no linked repositories to refresh.");
            return;
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;

        for (ProjectRepositoryLink link : candidateLinks) {
            attempted++;
            try {
                gitHubSyncService.syncRepository(link.getId());
                succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn(
                    "Scheduled GitHub sync failed linkId={} projectId={}: {}",
                    link.getId(),
                    link.getProjectId(),
                    exception.getMessage() == null ? "sync failed" : exception.getMessage()
                );
            }
        }

        LOGGER.info(
            "Scheduled GitHub heavy sync completed attempted={} succeeded={} failed={} batchSize={}",
            attempted,
            succeeded,
            failed,
            batchSize
        );
    }
}
