package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class JiraIssueSyncServiceImpl implements JiraIssueSyncService {

    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final ProjectJiraIntegrationRepository jiraIntegrationRepository;
    private final ProjectJiraIssueRepository jiraIssueRepository;
    private final JiraClient jiraClient;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;

    JiraIssueSyncServiceImpl(
            ProjectJiraIntegrationRepository jiraIntegrationRepository,
            ProjectJiraIssueRepository jiraIssueRepository,
            JiraClient jiraClient,
            JiraTokenEncryptionService jiraTokenEncryptionService) {
        this.jiraIntegrationRepository = jiraIntegrationRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraClient = jiraClient;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
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

        // Step 4: Build lookup map of existing cached issues for this project
        List<ProjectJiraIssue> existingIssues = jiraIssueRepository.findAllByProjectId(projectId);
        Map<String, ProjectJiraIssue> existingByKey = new HashMap<>();
        for (ProjectJiraIssue existing : existingIssues) {
            existingByKey.put(existing.getIssueKey(), existing);
        }

        // Step 5: Upsert — update existing rows, create new rows for unseen keys
        Instant now = Instant.now();
        List<ProjectJiraIssue> toSave = new ArrayList<>();
        for (JiraIssueDto dto : fetchedIssues) {
            String issueKey = dto.getKey();
            if (issueKey == null || issueKey.isBlank()) {
                continue;
            }
            ProjectJiraIssue issue = existingByKey.getOrDefault(issueKey, new ProjectJiraIssue());
            mapDtoToEntity(issue, dto, projectId, now);
            toSave.add(issue);
        }
        jiraIssueRepository.saveAll(toSave);

        // Step 6: Delete stale rows — only reached because the full fetch succeeded above
        List<String> fetchedKeys = fetchedIssues.stream()
                .map(JiraIssueDto::getKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!fetchedKeys.isEmpty()) {
            jiraIssueRepository.deleteAllByProjectIdAndIssueKeyNotIn(projectId, fetchedKeys);
        } else {
            // Jira returned zero issues — clear the entire project cache
            jiraIssueRepository.deleteAllByProjectId(projectId);
        }
    }

    private void mapDtoToEntity(
            ProjectJiraIssue issue,
            JiraIssueDto dto,
            UUID projectId,
            Instant syncedAt) {
        issue.setProjectId(projectId);
        issue.setIssueKey(dto.getKey());
        issue.setSyncedAt(syncedAt);

        JiraIssueDto.Fields fields = dto.getFields();
        if (fields == null) {
            return;
        }

        issue.setSummary(fields.getSummary());

        JiraIssueDto.IssueType issueType = fields.getIssueType();
        issue.setIssueType(issueType != null ? issueType.getName() : null);

        JiraIssueDto.Status status = fields.getStatus();
        if (status != null) {
            issue.setStatusName(status.getName());
            JiraIssueDto.StatusCategory category = status.getStatusCategory();
            issue.setStatusCategoryKey(category != null ? category.getKey() : null);
        }

        JiraIssueDto.Assignee assignee = fields.getAssignee();
        if (assignee != null) {
            issue.setAssigneeAccountId(assignee.getAccountId());
            issue.setAssigneeDisplayName(assignee.getDisplayName());
        }

        JiraIssueDto.Priority priority = fields.getPriority();
        issue.setPriorityName(priority != null ? priority.getName() : null);

        Double storyPoints = fields.getStoryPoints();
        issue.setStoryPoints(storyPoints != null ? BigDecimal.valueOf(storyPoints) : null);

        issue.setDueDate(parseLocalDate(fields.getDueDate()));
        issue.setResolutionDate(parseInstant(fields.getResolutionDate()));
        issue.setJiraCreatedAt(parseInstant(fields.getCreated()));
        issue.setJiraUpdatedAt(parseInstant(fields.getUpdated()));

        JiraIssueDto.Parent parent = fields.getParent();
        issue.setParentKey(parent != null ? parent.getKey() : null);
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, JIRA_TIMESTAMP_FORMAT).toInstant();
        } catch (Exception ex) {
            try {
                return Instant.parse(value);
            } catch (Exception fallback) {
                return null;
            }
        }
    }
}
