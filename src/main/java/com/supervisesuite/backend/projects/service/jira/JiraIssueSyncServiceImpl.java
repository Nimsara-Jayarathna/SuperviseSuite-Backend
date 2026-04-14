package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.service.SyncAttemptSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrator service adhering to SRP that delegates processing rules to a domain component.
 */
@Service
class JiraIssueSyncServiceImpl implements JiraIssueSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JiraIssueSyncServiceImpl.class);
    private static final String SYNC_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    private static final String SYNC_STATUS_FAILED = "FAILED";

    private final ProjectJiraIntegrationRepository jiraIntegrationRepository;
    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraClient jiraClient;
    private final JiraAuthManager jiraAuthManager;
    private final JiraIssueMapper jiraIssueMapper;
    private final JiraIssueSyncProcessor syncProcessor;
    private final TransactionTemplate transactionTemplate;
    private final JiraProperties jiraProperties;

    JiraIssueSyncServiceImpl(
            ProjectJiraIntegrationRepository jiraIntegrationRepository,
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraClient jiraClient,
            JiraAuthManager jiraAuthManager,
            JiraIssueMapper jiraIssueMapper,
            JiraIssueSyncProcessor syncProcessor,
            TransactionTemplate transactionTemplate,
            JiraProperties jiraProperties) {
        this.jiraIntegrationRepository = jiraIntegrationRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraClient = jiraClient;
        this.jiraAuthManager = jiraAuthManager;
        this.jiraIssueMapper = jiraIssueMapper;
        this.syncProcessor = syncProcessor;
        this.transactionTemplate = transactionTemplate;
        this.jiraProperties = jiraProperties;
    }

    @Override
    public void syncProjectIssues(UUID projectId) {
        syncProjectIssues(projectId, SyncAttemptSource.MANUAL);
    }

    @Override
    public void syncProjectIssues(UUID projectId, SyncAttemptSource source) {
        LOGGER.info("Jira sync attempt source={} projectId={}", source, projectId);
        Instant attemptAt = Instant.now();
        int timeoutSeconds = Math.max(30, jiraProperties.getJobs().getIssueSync().getInProgressTimeoutSeconds());
        Instant staleBefore = attemptAt.minusSeconds(timeoutSeconds);
        boolean claimed = transactionTemplate.execute(status -> jiraIntegrationRepository.claimForSync(
                projectId,
                SYNC_STATUS_IN_PROGRESS,
                attemptAt,
                attemptAt,
                staleBefore)) > 0;
        if (!claimed) {
            LOGGER.info(
                "Deferred Jira sync source={} projectId={} because another sync is already in progress.",
                source,
                projectId
            );
            return;
        }

        ProjectJiraIntegration integration = jiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId)
                .orElseThrow(() -> new ValidationException(
                        "jira", "No active Jira integration found for this project."));

        try {
            String accessToken = jiraAuthManager.getOrRefreshAccessToken(integration);

            List<JiraIssueDto> fetchedIssues = jiraClient.fetchAllIssues(
                    integration.getCloudId(), accessToken);

            List<JiraIssueDto> uniqueFetchedIssues = syncProcessor.deduplicate(fetchedIssues);

            saveSyncResults(projectId, uniqueFetchedIssues);
            markSyncSuccess(projectId);
            LOGGER.info("Jira sync completed source={} projectId={} status=SUCCESS", source, projectId);
        } catch (RuntimeException exception) {
            markSyncFailure(projectId, exception);
            LOGGER.warn(
                "Jira sync completed source={} projectId={} status=FAILED message={}",
                source,
                projectId,
                nullable(exception.getMessage(), "Jira issue sync failed.")
            );
            throw exception;
        }
    }

    private void saveSyncResults(UUID projectId, List<JiraIssueDto> uniqueFetchedIssues) {
        transactionTemplate.executeWithoutResult(status -> {
            List<ProjectJiraIssue> existingIssues = jiraIssueRepository.findAllByProjectId(projectId);
            Map<String, ProjectJiraIssue> existingByKey = new HashMap<>();
            for (ProjectJiraIssue existing : existingIssues) {
                existingByKey.put(existing.getIssueKey(), existing);
            }

            Instant now = Instant.now();
            List<ProjectJiraIssue> toSave = new ArrayList<>();
            for (JiraIssueDto dto : uniqueFetchedIssues) {
                String issueKey = dto.getKey();
                ProjectJiraIssue issue = existingByKey.getOrDefault(issueKey, new ProjectJiraIssue());
                jiraIssueMapper.mapToEntity(issue, dto, projectId, now);
                toSave.add(issue);
            }

            syncProcessor.processRelationships(toSave);

            jiraIssueRepository.saveAll(toSave);

            List<String> fetchedKeys = uniqueFetchedIssues.stream().map(JiraIssueDto::getKey).toList();

            if (!fetchedKeys.isEmpty()) {
                jiraIssueRepository.deleteAllByProjectIdAndIssueKeyNotIn(projectId, fetchedKeys);
            } else {
                jiraIssueRepository.deleteAllByProjectId(projectId);
            }
        });
    }

    private void markSyncSuccess(UUID projectId) {
        transactionTemplate.executeWithoutResult(status -> jiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId)
                .ifPresent(integration -> {
                    Instant now = Instant.now();
                    integration.setLastSyncedAt(now);
                    integration.setSyncStatus(SYNC_STATUS_SUCCESS);
                    integration.setSyncError(null);
                    integration.setUpdatedAt(now);
                    jiraIntegrationRepository.save(integration);
                }));
    }

    private void markSyncFailure(UUID projectId, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(status -> jiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId)
                .ifPresent(integration -> {
                    Instant now = Instant.now();
                    integration.setSyncStatus(SYNC_STATUS_FAILED);
                    integration.setSyncError(nullable(exception.getMessage(), "Jira issue sync failed."));
                    integration.setUpdatedAt(now);
                    jiraIntegrationRepository.save(integration);
                }));
    }

    private String nullable(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
