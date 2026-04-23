package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraAuthManagerImplTest {

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private ProjectJiraIntegrationRepository projectJiraIntegrationRepository;

    @Mock
    private JiraTokenEncryptionService jiraTokenEncryptionService;

    private JiraAuthManagerImpl authManager;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
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

        when(jiraProperties.getClientId()).thenReturn("client-id");
        when(jiraProperties.getClientSecret()).thenReturn("client-secret");
        when(jiraProperties.getTokenTargetUrl()).thenReturn("https://auth.atlassian.com/oauth/token");
        when(jiraTokenEncryptionService.decrypt(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0, String.class);
            return switch (encrypted) {
                case "enc:refresh-token" -> "refresh-token";
                case "enc:new-access-token" -> "new-access-token";
                default -> null;
            };
        });
        when(jiraTokenEncryptionService.encrypt("new-access-token")).thenReturn("enc:new-access-token");
        when(jiraTokenEncryptionService.encrypt("new-refresh-token")).thenReturn("enc:new-refresh-token");
        server.expect(once(), requestTo("https://auth.atlassian.com/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess("""
                {
                  "access_token": "new-access-token",
                  "refresh_token": "new-refresh-token",
                  "expires_in": 3600
                }
                """, MediaType.APPLICATION_JSON));

        String accessToken = authManager.getOrRefreshAccessToken(integration);

        assertThat(accessToken).isEqualTo("new-access-token");
        assertThat(integration.getAccessTokenEncrypted()).isEqualTo("enc:new-access-token");
        assertThat(integration.getRefreshTokenEncrypted()).isEqualTo("enc:new-refresh-token");
        assertThat(integration.getTokenExpiresAt()).isAfter(Instant.now());
        verify(projectJiraIntegrationRepository).saveAndFlush(integration);
        server.verify();
    }
}
