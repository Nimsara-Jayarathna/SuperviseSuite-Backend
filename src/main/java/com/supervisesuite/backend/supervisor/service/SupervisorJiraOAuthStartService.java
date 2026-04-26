package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.util.NormalizationUtils;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraOAuthState;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import com.supervisesuite.backend.users.entity.User;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SupervisorJiraOAuthStartService {

    private static final String DEFAULT_JIRA_SCOPE = "read:jira-user read:jira-work offline_access";
    private static final String REQUIRED_JIRA_SCOPE = "offline_access";

    private final JiraProperties jiraProperties;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository;
    private final SecureTokenService secureTokenService;

    SupervisorJiraOAuthStartService(
            JiraProperties jiraProperties,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            ProjectJiraOAuthStateRepository projectJiraOAuthStateRepository,
            SecureTokenService secureTokenService) {
        this.jiraProperties = jiraProperties;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.projectJiraOAuthStateRepository = projectJiraOAuthStateRepository;
        this.secureTokenService = secureTokenService;
    }

    JiraAuthUrlDto getProjectJiraAuthUrl(User supervisor, UUID parsedProjectId, Project project) {
        ProjectJiraIntegration activeIntegration = projectJiraIntegrationRepository
                .findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(project.getId())
                .orElse(null);
        if (activeIntegration != null) {
            String issue = "Jira is already connected for this project (" + activeIntegration.getWorkspaceName() + ").";
            throw new ValidationException(
                    issue,
                    List.of(new ApiErrorDetail("jira", issue)));
        }

        String clientId = NormalizationUtils.trimToNull(jiraProperties.getClientId());
        String redirectUri = NormalizationUtils.trimToNull(jiraProperties.getRedirectUri());
        if (clientId == null || redirectUri == null) {
            throw new ValidationException("jiraConfig", "Jira OAuth is not fully configured.");
        }

        String authTargetUrl = NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getAuthTargetUrl()),
                "https://auth.atlassian.com/authorize");
        String nonce = secureTokenService.generateOpaqueToken();
        String state = nonce + ":" + parsedProjectId;
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Math.max(60, jiraProperties.getOauthStateTtlSeconds()));

        ProjectJiraOAuthState oauthState = new ProjectJiraOAuthState();
        oauthState.setStateNonceHash(secureTokenService.sha256Base64(nonce));
        oauthState.setProjectId(parsedProjectId);
        oauthState.setUserId(supervisor.getId());
        oauthState.setExpiresAt(expiresAt);
        oauthState.setCreatedAt(now);
        oauthState.setUpdatedAt(now);
        projectJiraOAuthStateRepository.saveAndFlush(oauthState);

        String url = authTargetUrl
            + "?audience=" + urlencode(NormalizationUtils.defaultIfBlank(
                NormalizationUtils.trimToNull(jiraProperties.getAudience()),
                "api.atlassian.com"))
            + "&client_id=" + urlencode(clientId)
            + "&scope=" + urlencode(normalizeJiraScope(jiraProperties.getScope()))
            + "&redirect_uri=" + urlencode(redirectUri)
            + "&state=" + urlencode(state)
            + "&response_type=code&prompt=consent";
        return new JiraAuthUrlDto(url);
    }

    private String normalizeJiraScope(String configuredScope) {
        String scope = NormalizationUtils.defaultIfBlank(NormalizationUtils.trimToNull(configuredScope), DEFAULT_JIRA_SCOPE);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : scope.split("\\s+")) {
            String trimmed = NormalizationUtils.trimToNull(token);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        normalized.add(REQUIRED_JIRA_SCOPE);
        return String.join(" ", normalized);
    }

    private String urlencode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
