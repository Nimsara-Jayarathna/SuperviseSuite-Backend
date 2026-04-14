package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.common.scheduler.SystemSyncProvider;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class JiraIssueSyncProvider implements SystemSyncProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraIssueSyncProvider.class);

    private final JiraProperties jiraProperties;
    private final ProjectJiraIntegrationRepository jiraIntegrationRepository;
    private final JiraIssueSyncService jiraIssueSyncService;

    public JiraIssueSyncProvider(
        JiraProperties jiraProperties,
        ProjectJiraIntegrationRepository jiraIntegrationRepository,
        JiraIssueSyncService jiraIssueSyncService
    ) {
        this.jiraProperties = jiraProperties;
        this.jiraIntegrationRepository = jiraIntegrationRepository;
        this.jiraIssueSyncService = jiraIssueSyncService;
    }

    @Override
    public void executeSync() {
        JiraProperties.Jobs.IssueSync config = jiraProperties.getJobs().getIssueSync();
        if (config == null || !config.isEnabled()) {
            return;
        }

        int batchSize = Math.max(1, config.getBatchSize());
        Page<ProjectJiraIntegration> candidateIntegrations = jiraIntegrationRepository
            .findByRevokedAtIsNullOrderByConnectedAtAsc(PageRequest.of(0, batchSize));

        if (candidateIntegrations.isEmpty()) {
            LOGGER.info("Jira issue sync provider found no active integrations to refresh.");
            return;
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;

        for (ProjectJiraIntegration integration : candidateIntegrations.getContent()) {
            attempted++;
            try {
                jiraIssueSyncService.syncProjectIssues(integration.getProjectId());
                succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn(
                    "Scheduled Jira sync failed projectId={}: {}",
                    integration.getProjectId(),
                    exception.getMessage() == null ? "sync failed" : exception.getMessage()
                );
            }
        }

        LOGGER.info(
            "Scheduled Jira heavy sync completed attempted={} succeeded={} failed={} batchSize={}",
            attempted,
            succeeded,
            failed,
            batchSize
        );
    }
}
