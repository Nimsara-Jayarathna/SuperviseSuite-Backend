package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class JiraAuthManagerImplTest {

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private ProjectJiraIntegrationRepository projectJiraIntegrationRepository;

    @Mock
    private JiraTokenEncryptionService jiraTokenEncryptionService;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private JiraAuthManagerImpl authManager;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        authManager = new JiraAuthManagerImpl(
            jiraProperties,
            projectJiraIntegrationRepository,
            jiraTokenEncryptionService,
            restClientBuilder
        );
    }

    @Test
    void getOrRefreshAccessToken_whenExpiredWithoutRefreshToken_throwsReconnectRequired() {
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(UUID.randomUUID());
        integration.setAccessTokenEncrypted("enc:expired-access");
        integration.setTokenExpiresAt(Instant.now().minusSeconds(5));

        assertThatThrownBy(() -> authManager.getOrRefreshAccessToken(integration))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("cannot be refreshed")
            .hasMessageContaining("Please reconnect Jira");

        verify(jiraTokenEncryptionService, never()).decrypt("enc:expired-access");
    }

    @Test
    void getOrRefreshAccessToken_whenExpiredWithRefreshToken_refreshesAndReturnsUpdatedAccessToken() {
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(UUID.randomUUID());
        integration.setAccessTokenEncrypted("enc:expired-access");
        integration.setRefreshTokenEncrypted("enc:refresh-token");
        integration.setTokenExpiresAt(Instant.now().minusSeconds(5));

        Map<String, Object> tokenResponse = new LinkedHashMap<>();
        tokenResponse.put("access_token", "new-access-token");
        tokenResponse.put("refresh_token", "new-refresh-token");
        tokenResponse.put("expires_in", 3600);

        when(jiraProperties.getClientId()).thenReturn("client-id");
        when(jiraProperties.getClientSecret()).thenReturn("client-secret");
        when(jiraProperties.getTokenTargetUrl()).thenReturn("https://auth.atlassian.com/oauth/token");
        when(jiraTokenEncryptionService.decrypt("enc:refresh-token")).thenReturn("refresh-token");
        when(jiraTokenEncryptionService.encrypt("new-access-token")).thenReturn("enc:new-access-token");
        when(jiraTokenEncryptionService.encrypt("new-refresh-token")).thenReturn("enc:new-refresh-token");
        when(jiraTokenEncryptionService.decrypt("enc:new-access-token")).thenReturn("new-access-token");
        when(restClient.post()
                .uri("https://auth.atlassian.com/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(any())
                .retrieve()
                .body(Map.class))
            .thenReturn(tokenResponse);

        String accessToken = authManager.getOrRefreshAccessToken(integration);

        assertThat(accessToken).isEqualTo("new-access-token");
        assertThat(integration.getAccessTokenEncrypted()).isEqualTo("enc:new-access-token");
        assertThat(integration.getRefreshTokenEncrypted()).isEqualTo("enc:new-refresh-token");
        assertThat(integration.getTokenExpiresAt()).isAfter(Instant.now());
        verify(projectJiraIntegrationRepository).saveAndFlush(integration);
    }
}
