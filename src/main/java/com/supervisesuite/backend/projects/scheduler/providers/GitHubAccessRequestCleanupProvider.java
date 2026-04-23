package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GitHubAccessRequestCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubAccessRequestCleanupProvider.class);
    private static final String ACCESS_REQUEST_STATUS_PENDING = "PENDING";
    private static final String ACCESS_REQUEST_STATUS_EXPIRED = "EXPIRED";

    private final GitHubProperties gitHubProperties;
    private final ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;

    public GitHubAccessRequestCleanupProvider(
        GitHubProperties gitHubProperties,
        ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository
    ) {
        this.gitHubProperties = gitHubProperties;
        this.projectGitHubAccessRequestRepository = projectGitHubAccessRequestRepository;
    }

    @Override
    @Transactional
    public void executeCleanup() {
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
}
