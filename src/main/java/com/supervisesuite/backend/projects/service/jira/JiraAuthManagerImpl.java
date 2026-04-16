package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
class JiraAuthManagerImpl implements JiraAuthManager {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(JiraAuthManagerImpl.class);
    private static final long REFRESH_SKEW_SECONDS = 60L;

    private final JiraProperties jiraProperties;
    private final ProjectJiraIntegrationRepository projectJiraIntegrationRepository;
    private final JiraTokenEncryptionService jiraTokenEncryptionService;
    private final RestClient restClient;

    JiraAuthManagerImpl(
            JiraProperties jiraProperties,
            ProjectJiraIntegrationRepository projectJiraIntegrationRepository,
            JiraTokenEncryptionService jiraTokenEncryptionService,
            RestClient.Builder restClientBuilder) {
        this.jiraProperties = jiraProperties;
        this.projectJiraIntegrationRepository = projectJiraIntegrationRepository;
        this.jiraTokenEncryptionService = jiraTokenEncryptionService;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String getOrRefreshAccessToken(ProjectJiraIntegration integration) {
        Instant now = Instant.now();
        Instant tokenExpiresAt = integration.getTokenExpiresAt();
        boolean tokenExpired = tokenExpiresAt != null && !now.isBefore(tokenExpiresAt);

        // Refresh shortly before expiry to avoid sending a token that is about to expire.
        if (tokenExpiresAt != null && now.isAfter(tokenExpiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
            String refreshTokenEncrypted = trimToNull(integration.getRefreshTokenEncrypted());
            if (refreshTokenEncrypted == null) {
                if (tokenExpired) {
                    throw new ServiceUnavailableException(
                            "Jira access token has expired and this Jira connection cannot be refreshed. "
                                    + "Please reconnect Jira from the Integrations tab.");
                }
            } else {
                try {
                    String refreshToken = jiraTokenEncryptionService.decrypt(refreshTokenEncrypted);
                    String clientId = trimToNull(jiraProperties.getClientId());
                    String clientSecret = trimToNull(jiraProperties.getClientSecret());
                    String targetUrl = trimToNull(jiraProperties.getTokenTargetUrl());
                    String tokenTargetUrl = targetUrl == null ? "https://auth.atlassian.com/oauth/token" : targetUrl;

                    if (clientId == null || clientSecret == null) {
                        if (tokenExpired) {
                            throw new ServiceUnavailableException(
                                    "Jira access token has expired and Jira OAuth refresh is not fully configured. "
                                            + "Please check Atlassian client credentials.");
                        }
                        return jiraTokenEncryptionService.decrypt(integration.getAccessTokenEncrypted());
                    }

                    Map<String, Object> tokenRequest = new LinkedHashMap<>();
                    tokenRequest.put("grant_type", "refresh_token");
                    tokenRequest.put("client_id", clientId);
                    tokenRequest.put("client_secret", clientSecret);
                    tokenRequest.put("refresh_token", refreshToken);

                    Map<?, ?> tokenResponse = restClient.post()
                            .uri(tokenTargetUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(tokenRequest)
                            .retrieve()
                            .body(Map.class);

                    String newAccessToken = tokenResponse == null ? null : trimToNull(String.valueOf(tokenResponse.get("access_token")));
                    String newRefreshToken = tokenResponse == null ? null : trimToNull(String.valueOf(tokenResponse.get("refresh_token")));
                    Number expiresInSecs = tokenResponse == null || tokenResponse.get("expires_in") == null ? null : (Number) tokenResponse.get("expires_in");

                    if (newAccessToken != null && !"null".equalsIgnoreCase(newAccessToken)) {
                        integration.setAccessTokenEncrypted(jiraTokenEncryptionService.encrypt(newAccessToken));
                        if (newRefreshToken != null && !"null".equalsIgnoreCase(newRefreshToken)) {
                            integration.setRefreshTokenEncrypted(jiraTokenEncryptionService.encrypt(newRefreshToken));
                        }
                        if (expiresInSecs != null) {
                            integration.setTokenExpiresAt(Instant.now().plusSeconds(expiresInSecs.longValue()));
                        }
                        projectJiraIntegrationRepository.saveAndFlush(integration);
                    }
                } catch (RestClientResponseException ex) {
                    log.warn("Jira token refresh rejected by Atlassian for project {}. Status: {}, Body: {}",
                            integration.getProjectId(),
                            ex.getStatusCode(),
                            ex.getResponseBodyAsString());
                    throw new ServiceUnavailableException(
                            "Jira access token has expired and Atlassian rejected the refresh "
                                    + "request. Please reconnect Jira from the Integrations tab.");
                } catch (ResourceAccessException ex) {
                    log.warn("Jira token refresh network error for project {} — proceeding with existing token. Error: {}",
                            integration.getProjectId(), ex.getMessage());
                    if (tokenExpired) {
                        throw new ServiceUnavailableException(
                                "Jira access token has expired and could not be refreshed. "
                                        + "Please reconnect Jira from the Integrations tab.");
                    }
                } catch (Exception ex) {
                    log.warn("Jira token refresh unexpected error for project {}. Error: {}",
                            integration.getProjectId(), ex.getMessage());
                    throw new ServiceUnavailableException(
                            "Jira access token has expired and could not be refreshed. "
                                    + "Please reconnect Jira from the Integrations tab.");
                }
            }
        }

        // Return the decrypted valid token
        return jiraTokenEncryptionService.decrypt(integration.getAccessTokenEncrypted());
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
