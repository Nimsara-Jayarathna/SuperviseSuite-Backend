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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class JiraIssueSyncServiceImpl implements JiraIssueSyncService {

    private final ProjectJiraIntegrationRepository jiraIntegrationRepository;
    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraClient jiraClient;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final JiraIssueMapper jiraIssueMapper;

    JiraIssueSyncServiceImpl(
            ProjectJiraIntegrationRepository jiraIntegrationRepository,
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraClient jiraClient,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            JiraIssueMapper jiraIssueMapper) {
        this.jiraIntegrationRepository = jiraIntegrationRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraClient = jiraClient;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
        this.jiraIssueMapper = jiraIssueMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncProjectIssues(UUID projectId) {
        // Step 1: Resolve active integration
        ProjectJiraIntegration integration = jiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId)
                .orElseThrow(() -> new ValidationException(
                        "jira", "No active Jira integration found for this project."));

        // Step 2: Decrypt access token
        String accessToken = jiraTokenEncryptionService.decrypt(
                integration.getAccessTokenEncrypted());

        // Step 3: Fetch all issues from Jira — any exception here aborts before any DB write
        List<JiraIssueDto> fetchedIssues = jiraClient.fetchAllIssues(
                integration.getCloudId(), accessToken);

        // Step 3.1: Jira can occasionally return duplicate issue keys across pages.
        // Deduplicate to avoid unique-constraint conflicts on (project_id, issue_key).
        Map<String, JiraIssueDto> uniqueDtosByKey = new LinkedHashMap<>();
        for (JiraIssueDto dto : fetchedIssues) {
            String issueKey = dto.getKey();
            if (issueKey == null || issueKey.isBlank()) {
                continue;
            }
            uniqueDtosByKey.putIfAbsent(issueKey, dto);
        }
        List<JiraIssueDto> uniqueFetchedIssues = new ArrayList<>(uniqueDtosByKey.values());

        // Step 4: Build lookup map of existing cached issues for this project
        List<ProjectJiraIssue> existingIssues = jiraIssueRepository.findAllByProjectId(projectId);
        Map<String, ProjectJiraIssue> existingByKey = new HashMap<>();
        for (ProjectJiraIssue existing : existingIssues) {
            existingByKey.put(existing.getIssueKey(), existing);
        }

        // Step 5: Upsert — update existing rows, create new rows for unseen keys
        Instant now = Instant.now();
        List<ProjectJiraIssue> toSave = new ArrayList<>();
        for (JiraIssueDto dto : uniqueFetchedIssues) {
            String issueKey = dto.getKey();
            ProjectJiraIssue issue = existingByKey.getOrDefault(issueKey, new ProjectJiraIssue());
            jiraIssueMapper.mapToEntity(issue, dto, projectId, now);
            toSave.add(issue);
        }
        jiraIssueRepository.saveAll(toSave);

        // Step 6: Delete stale rows — only reached because the full fetch succeeded above
        List<String> fetchedKeys = new ArrayList<>(uniqueDtosByKey.keySet());

        if (!fetchedKeys.isEmpty()) {
            jiraIssueRepository.deleteAllByProjectIdAndIssueKeyNotIn(projectId, fetchedKeys);
        } else {
            // Jira returned zero issues — clear the entire project cache
            jiraIssueRepository.deleteAllByProjectId(projectId);
        }
    }

}
