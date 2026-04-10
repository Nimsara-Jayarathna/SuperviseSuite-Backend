package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service adhering to SRP that delegates processing rules to a domain component.
 */
@Service
class JiraIssueSyncServiceImpl implements JiraIssueSyncService {

    private final ProjectJiraIntegrationRepository jiraIntegrationRepository;
    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraClient jiraClient;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final JiraIssueMapper jiraIssueMapper;
    private final JiraIssueSyncProcessor syncProcessor;

    JiraIssueSyncServiceImpl(
            ProjectJiraIntegrationRepository jiraIntegrationRepository,
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraClient jiraClient,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            JiraIssueMapper jiraIssueMapper,
            JiraIssueSyncProcessor syncProcessor) {
        this.jiraIntegrationRepository = jiraIntegrationRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraClient = jiraClient;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
        this.jiraIssueMapper = jiraIssueMapper;
        this.syncProcessor = syncProcessor;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncProjectIssues(UUID projectId) {
        ProjectJiraIntegration integration = jiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId)
                .orElseThrow(() -> new ValidationException(
                        "jira", "No active Jira integration found for this project."));

        String accessToken = jiraTokenEncryptionService.decrypt(
                integration.getAccessTokenEncrypted());

        List<JiraIssueDto> fetchedIssues = jiraClient.fetchAllIssues(
                integration.getCloudId(), accessToken);

        List<JiraIssueDto> uniqueFetchedIssues = syncProcessor.deduplicate(fetchedIssues);

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
    }
}
