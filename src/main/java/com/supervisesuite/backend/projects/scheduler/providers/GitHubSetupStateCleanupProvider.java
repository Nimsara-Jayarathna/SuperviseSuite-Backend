package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.projects.repository.GitHubSetupStateRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GitHubSetupStateCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubSetupStateCleanupProvider.class);

    private final GitHubSetupStateRepository gitHubSetupStateRepository;

    public GitHubSetupStateCleanupProvider(GitHubSetupStateRepository gitHubSetupStateRepository) {
        this.gitHubSetupStateRepository = gitHubSetupStateRepository;
    }

    @Override
    @Transactional
    public void executeCleanup() {
        int deleted = gitHubSetupStateRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            LOGGER.info("GitHub setup state cleanup removed {} expired state(s).", deleted);
        }
    }
}
