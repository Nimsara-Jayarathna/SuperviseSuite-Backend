package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import com.supervisesuite.backend.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SupervisorJiraOAuthCompletionService {

    private final JiraProperties jiraProperties;
    private final ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final SecureTokenService secureTokenService;
    private final AtlassianOAuthClient atlassianOAuthClient;
    private final SupervisorJiraWorkspaceSelectionStore workspaceSelectionStore;
    private final SupervisorJiraIntegrationWriter integrationWriter;

    SupervisorJiraOAuthCompletionService(
            JiraProperties jiraProperties,
            ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository,
            ProjectAccessGuard projectAccessGuard,
            SecureTokenService secureTokenService,
            AtlassianOAuthClient atlassianOAuthClient,
            SupervisorJiraWorkspaceSelectionStore workspaceSelectionStore,
            SupervisorJiraIntegrationWriter integrationWriter) {
        this.jiraProperties = jiraProperties;
        this.projectJiraOAuthStateRepository = projectJiraOAuthStateRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.secureTokenService = secureTokenService;
        this.atlassianOAuthClient = atlassianOAuthClient;
        this.workspaceSelectionStore = workspaceSelectionStore;
        this.integrationWriter = integrationWriter;
    }

    JiraOAuthCompleteResultDto completeJiraOAuth(User supervisor, JiraOAuthCompleteRequestDto request) {
        workspaceSelectionStore.cleanupExpired(Instant.now());

        String selectionToken = NormalizationUtils.trimToNull(request.getSelectionToken());
        if (selectionToken != null) {
            return completeJiraOAuthWithWorkspaceSelection(supervisor, request, selectionToken);
        }

        String oauthError = NormalizationUtils.trimToNull(request.getError());
        String code = NormalizationUtils.trimToNull(request.getCode());
        String state = NormalizationUtils.trimToNull(request.getState());
        if (oauthError != null) {
            String issue = "access_denied".equalsIgnoreCase(oauthError)
                ? "Jira authorization was cancelled. No connection was saved."
                : "Jira authorization was not completed. "
                    + NormalizationUtils.defaultIfBlank(
                        NormalizationUtils.trimToNull(request.getErrorDescription()),
                        "Please try again.");
            throw new ValidationException(
                    issue,
                    List.of(new ApiErrorDetail("jiraOAuth", issue)));
        }

        if (code == null || state == null) {
            throw new ValidationException("jiraOAuth", "Missing OAuth code/state.");
        }

        String nonce = NormalizationUtils.trimToNull(state.split(":", 2)[0]);
        String projectIdRaw = state.contains(":") ? state.split(":", 2)[1] : null;
        if (nonce == null || projectIdRaw == null) {
            throw new ValidationException("state", "OAuth state format is invalid. Start Jira connection again.");
        }
        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("state", "Invalid OAuth state project reference.");
        }

        String nonceHash = secureTokenService.sha256Base64(nonce);
        Optional<ProjectJiraOAuthState> persistedStateOpt = projectJiraOAuthStateRepository.findByStateNonceHash(nonceHash);
        if (persistedStateOpt.isEmpty()) {
            throw new ValidationException("state", "OAuth state was not recognized. Start Jira connection again.");
        }
        ProjectJiraOAuthState persistedState = persistedStateOpt.get();
        Instant now = Instant.now();
        if (persistedState.getUsedAt() != null) {
            throw new ValidationException("state", "OAuth state was already used. Start Jira connection again.");
        }
        if (persistedState.getExpiresAt() == null || now.isAfter(persistedState.getExpiresAt())) {
            throw new ValidationException("state", "OAuth state expired. Start Jira connection again.");
        }
        if (!projectId.equals(persistedState.getProjectId())) {
            throw new ValidationException("state", "OAuth state project does not match this callback.");
        }
        if (!supervisor.getId().equals(persistedState.getUserId())) {
            throw new ValidationException("state", "OAuth state user does not match the current session.");
        }
        persistedState.setUsedAt(now);
        persistedState.setUpdatedAt(now);
        projectJiraOAuthStateRepository.save(persistedState);

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, projectId);

        String clientId = NormalizationUtils.trimToNull(jiraProperties.getClientId());
        String clientSecret = NormalizationUtils.trimToNull(jiraProperties.getClientSecret());
        String redirectUri = NormalizationUtils.trimToNull(jiraProperties.getRedirectUri());
        if (clientId == null || clientSecret == null || redirectUri == null) {
            throw new ValidationException("jiraConfig", "Jira OAuth is not fully configured.");
        }

        String tokenTargetUrl = NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getTokenTargetUrl()),
                "https://auth.atlassian.com/oauth/token");

        AtlassianOAuthClient.TokenExchangeResult token = atlassianOAuthClient.exchangeAuthorizationCode(
                tokenTargetUrl,
                clientId,
                clientSecret,
                code,
                redirectUri);
        List<Map<String, Object>> resources = atlassianOAuthClient.getAccessibleResources(token.accessToken());

        if (resources.size() > 1) {
            String pendingToken = secureTokenService.generateOpaqueToken();
            List<JiraOAuthCompleteResultDto.WorkspaceOption> workspaceOptions = mapWorkspaceOptions(resources);
            workspaceSelectionStore.put(
                    pendingToken,
                    project.getId(),
                    supervisor.getId(),
                    token.accessToken(),
                    token.refreshToken(),
                    token.tokenExpiresAt(),
                    token.scopes(),
                    workspaceOptions);

            return new JiraOAuthCompleteResultDto(
                    project.getId().toString(),
                    null,
                    true,
                    pendingToken,
                    workspaceOptions);
        }

        Map<String, Object> workspace = resources.get(0);
        return integrationWriter.persistWorkspaceIntegration(
                supervisor,
                project,
                token.accessToken(),
                token.refreshToken(),
                token.tokenExpiresAt(),
                token.scopes(),
                workspace);
    }

    private JiraOAuthCompleteResultDto completeJiraOAuthWithWorkspaceSelection(
            User supervisor,
            JiraOAuthCompleteRequestDto request,
            String selectionToken) {
        SupervisorJiraWorkspaceSelectionStore.PendingJiraWorkspaceSelection pending =
                workspaceSelectionStore.get(selectionToken);
        if (pending == null) {
            throw new ValidationException(
                    "jiraOAuth",
                    "Jira workspace selection has expired. Start Jira connection again.");
        }

        if (!supervisor.getId().equals(pending.userId())) {
            throw new ValidationException("jiraOAuth", "Jira workspace selection does not match the current session.");
        }

        if (Instant.now().isAfter(pending.expiresAt())) {
            workspaceSelectionStore.remove(selectionToken);
            throw new ValidationException(
                    "jiraOAuth",
                    "Jira workspace selection has expired. Start Jira connection again.");
        }

        String selectedCloudId = NormalizationUtils.trimToNull(request.getSelectedCloudId());
        if (selectedCloudId == null) {
            throw new ValidationException("jiraOAuth", "Select a Jira workspace to continue.");
        }

        JiraOAuthCompleteResultDto.WorkspaceOption selectedWorkspace = pending.workspaceOptions().stream()
                .filter(option -> selectedCloudId.equals(option.cloudId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "jiraOAuth",
                        "Selected Jira workspace is invalid. Please choose from the provided list."));

        Project project = projectAccessGuard.requireSupervisorOwnsProject(supervisor, pending.projectId());

        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("id", selectedWorkspace.cloudId());
        workspace.put("name", selectedWorkspace.workspaceName());
        workspace.put("url", selectedWorkspace.workspaceUrl());

        JiraOAuthCompleteResultDto result = integrationWriter.persistWorkspaceIntegration(
                supervisor,
                project,
                pending.accessToken(),
                pending.refreshToken(),
                pending.tokenExpiresAt(),
                pending.scopes(),
                workspace);

        workspaceSelectionStore.remove(selectionToken);
        return result;
    }

    private List<JiraOAuthCompleteResultDto.WorkspaceOption> mapWorkspaceOptions(List<Map<String, Object>> resources) {
        List<JiraOAuthCompleteResultDto.WorkspaceOption> options = new ArrayList<>();
        for (Map<String, Object> resource : resources) {
            String cloudId = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("id"));
            String workspaceName = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("name"));
            String workspaceUrl = NormalizationUtils.trimToNull(resource == null ? null : (String) resource.get("url"));
            if (cloudId == null || workspaceName == null) {
                continue;
            }
            options.add(new JiraOAuthCompleteResultDto.WorkspaceOption(cloudId, workspaceName, workspaceUrl));
        }

        if (options.isEmpty()) {
            throw new ValidationException("jiraOAuth", "Jira workspace details are incomplete.");
        }

        return options;
    }
}
