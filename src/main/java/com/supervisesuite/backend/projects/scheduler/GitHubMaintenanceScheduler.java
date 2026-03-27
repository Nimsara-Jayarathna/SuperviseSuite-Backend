package com.supervisesuite.backend.projects.scheduler;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GitHubMaintenanceScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubMaintenanceScheduler.class);
    private static final String PROVIDER_GITHUB = "github";
    private static final String ACCESS_REQUEST_STATUS_PENDING = "PENDING";
    private static final String ACCESS_REQUEST_STATUS_EXPIRED = "EXPIRED";

    private final GitHubProperties gitHubProperties;
    private final ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;
    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final GitHubSyncService gitHubSyncService;

    public GitHubMaintenanceScheduler(
        GitHubProperties gitHubProperties,
        ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository,
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        GitHubSyncService gitHubSyncService
    ) {
        this.gitHubProperties = gitHubProperties;
        this.projectGitHubAccessRequestRepository = projectGitHubAccessRequestRepository;
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.gitHubSyncService = gitHubSyncService;
    }

    @Scheduled(
        initialDelayString = "${app.github.jobs.access-request-cleanup.initial-delay-ms:120000}",
        fixedDelayString = "${app.github.jobs.access-request-cleanup.fixed-delay-ms:900000}"
    )
    @Transactional
    public void cleanupExpiredAccessRequestTokens() {
        GitHubProperties.AccessRequestCleanup config = gitHubProperties.getJobs().getAccessRequestCleanup();
        if (config == null || !config.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        int deletedExpiredRequests = projectGitHubAccessRequestRepository.deleteExpiredRequestsByStatuses(
            List.of(ACCESS_REQUEST_STATUS_PENDING, ACCESS_REQUEST_STATUS_EXPIRED),
            now
        );
        int clearedExpiredResultTokens = projectGitHubAccessRequestRepository.clearExpiredResultTokens(now);

        if (deletedExpiredRequests > 0 || clearedExpiredResultTokens > 0) {
            LOGGER.info(
                "GitHub access-request cleanup completed deletedExpiredRequests={} clearedExpiredResultTokens={}",
                deletedExpiredRequests,
                clearedExpiredResultTokens
            );
        }
    }

    @Scheduled(
        cron = "${app.github.jobs.repository-refresh.cron:0 0 0 * * *}",
        zone = "${app.github.jobs.repository-refresh.zone:UTC}"
    )
    public void refreshLinkedGitHubRepositories() {
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
            LOGGER.info("GitHub repository refresh scheduler found no linked repositories to refresh.");
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
                    "Scheduled GitHub refresh failed linkId={} projectId={}: {}",
                    link.getId(),
                    link.getProjectId(),
                    exception.getMessage() == null ? "refresh failed" : exception.getMessage()
                );
            }
        }

        LOGGER.info(
            "Scheduled GitHub refresh completed attempted={} succeeded={} failed={} batchSize={}",
            attempted,
            succeeded,
            failed,
            batchSize
        );
    }
}
