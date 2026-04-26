package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import com.supervisesuite.backend.projects.service.jira.JiraTokenEncryptionService;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class SupervisorJiraIntegrationWriter {

    private static final Logger log = LoggerFactory.getLogger(SupervisorJiraIntegrationWriter.class);

    private final ProjectRepository projectRepository;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final ProjectJiraIssueRepository projectJiraIssueRepository;
    private final JiraIssueSyncService jiraIssueSyncService;
    private final SupervisorProjectDtoMapper projectDtoMapper;

    SupervisorJiraIntegrationWriter(
            ProjectRepository projectRepository,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            ProjectJiraIssueRepository projectJiraIssueRepository,
            JiraIssueSyncService jiraIssueSyncService,
            SupervisorProjectDtoMapper projectDtoMapper) {
        this.projectRepository = projectRepository;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
        this.projectJiraIssueRepository = projectJiraIssueRepository;
        this.jiraIssueSyncService = jiraIssueSyncService;
        this.projectDtoMapper = projectDtoMapper;
    }

    JiraOAuthCompleteResultDto persistWorkspaceIntegration(
            User supervisor,
            Project project,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes,
            Map<String, Object> workspace) {
        String cloudId = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("id"));
        String workspaceName = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("name"));
        String workspaceUrl = NormalizationUtils.trimToNull(workspace == null ? null : (String) workspace.get("url"));
        if (cloudId == null || workspaceName == null) {
            throw new ValidationException("jiraOAuth", "Jira workspace details are incomplete.");
        }

        Instant revokeTimestamp = Instant.now();
        projectJiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .ifPresent(existing -> {
                    existing.setRevokedAt(revokeTimestamp);
                    existing.setUpdatedAt(revokeTimestamp);
                    projectJiraIntegrationRepository.save(existing);
                });

        Instant now = Instant.now();
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(project.getId());
        integration.setCloudId(cloudId);
        integration.setWorkspaceName(workspaceName);
        integration.setWorkspaceUrl(workspaceUrl);
        integration.setAccessTokenEncrypted(jiraTokenEncryptionService.encrypt(accessToken));
        integration.setRefreshTokenEncrypted(refreshToken == null || "null".equalsIgnoreCase(refreshToken) ? null : jiraTokenEncryptionService.encrypt(refreshToken));
        integration.setTokenExpiresAt(tokenExpiresAt);
        integration.setScope(scopes);
        integration.setConnectedBy(supervisor.getId());
        integration.setConnectedAt(now);
        integration.setUpdatedAt(now);
        projectJiraIntegrationRepository.save(integration);

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        projectRepository.save(project);

        triggerInitialJiraSyncAfterCommit(project.getId());

        return new JiraOAuthCompleteResultDto(
                project.getId().toString(),
                workspaceName,
                false,
                null,
                List.of());
    }

    SupervisorProjectDetailDto disconnectProjectJira(Project project) {
        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration == null) {
            throw new ValidationException(
                    "Jira is not connected for this project.",
                    List.of(new ApiErrorDetail("jira", "Jira is not connected for this project.")));
        }
        if ("IN_PROGRESS".equals(activeIntegration.getSyncStatus())) {
            throw new ConflictException("Cannot disconnect Jira while sync is in progress.");
        }

        Instant now = Instant.now();
        projectJiraIntegrationRepository.delete(activeIntegration);
        projectJiraIssueRepository.deleteAllByProjectId(project.getId());

        project.setUpdatedAt(now);
        project.setLastActivityAt(now);
        Project savedProject = projectRepository.save(project);
        return projectDtoMapper.toProjectDetail(savedProject != null ? savedProject : project);
    }

    private void triggerInitialJiraSyncAfterCommit(UUID projectId) {
        Runnable syncTask = () -> {
            try {
                jiraIssueSyncService.syncProjectIssues(projectId);
            } catch (Exception ex) {
                log.warn("Initial Jira issue sync failed for project {} after OAuth completion. "
                                + "Connection was saved successfully; cache will populate on next sync. Error: {}",
                        projectId,
                        ex.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncTask.run();
                }
            });
            return;
        }

        syncTask.run();
    }
}
