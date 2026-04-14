package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemCleanupProvider;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JiraOAuthStateCleanupProvider implements SystemCleanupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraOAuthStateCleanupProvider.class);

    private final ProjectJiraOAuthStateRepository jiraOAuthStateRepository;

    public JiraOAuthStateCleanupProvider(ProjectJiraOAuthStateRepository jiraOAuthStateRepository) {
        this.jiraOAuthStateRepository = jiraOAuthStateRepository;
    }

    @Override
    @Transactional
    public void executeCleanup() {
        int deleted = jiraOAuthStateRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            LOGGER.info("Jira OAuth state cleanup removed {} expired state(s).", deleted);
        }
    }
}
