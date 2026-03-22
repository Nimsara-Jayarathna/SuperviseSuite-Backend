package com.supervisesuite.backend.projects.scheduler;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final ProjectRepositoryCacheRepository projectRepositoryCacheRepository;
    private final com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository;
    private final ProjectService projectService;

    public GitHubMaintenanceScheduler(
        GitHubProperties gitHubProperties,
        ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository,
        ProjectRepositoryCacheRepository projectRepositoryCacheRepository,
        com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository,
        ProjectService projectService
    ) {
        this.gitHubProperties = gitHubProperties;
        this.projectGitHubAccessRequestRepository = projectGitHubAccessRequestRepository;
        this.projectRepositoryCacheRepository = projectRepositoryCacheRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
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
        List<ProjectRepository> candidateRepositories = projectRepositoryCacheRepository
            .findByProviderIgnoreCaseAndIsPrimaryTrueAndRepositoryUrlIsNotNull(
                PROVIDER_GITHUB,
                PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "updatedAt"))
            )
            .getContent();

        if (candidateRepositories.isEmpty()) {
            LOGGER.info("GitHub repository refresh scheduler found no linked repositories to refresh.");
            return;
        }

        Set<UUID> activeProjectIds = projectRepository
            .findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
                candidateRepositories.stream().map(ProjectRepository::getProjectId).toList()
            )
            .stream()
            .map(com.supervisesuite.backend.projects.entity.Project::getId)
            .collect(Collectors.toSet());

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;

        for (ProjectRepository repository : candidateRepositories) {
            if (repository.getProjectId() == null || repository.getRepositoryUrl() == null) {
                continue;
            }
            if (!activeProjectIds.contains(repository.getProjectId())) {
                continue;
            }

            attempted++;
            try {
                projectService.refreshGitHubData(repository.getProjectId(), repository.getRepositoryUrl());
                succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn(
                    "Scheduled GitHub refresh failed projectId={} repositoryUrl={}: {}",
                    repository.getProjectId(),
                    repository.getRepositoryUrl(),
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
