package com.supervisesuite.backend.projects.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GitHubAppControllerTest {

    @Mock
    private GitHubAppIntegrationService gitHubAppIntegrationService;

    @Mock
    private ApiResponseFactory apiResponseFactory;

    @Mock
    private HttpServletRequest request;

    private GitHubAppController controller;

    @BeforeEach
    void setUp() {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setBaseUrl("http://localhost:5173");
        controller = new GitHubAppController(
            gitHubAppIntegrationService,
            apiResponseFactory,
            frontendProperties,
            new ObjectMapper()
        );
    }

    @Test
    void handleSetup_requestFlowCompleted_redirectsToAccessUpdatedPage() {
        UUID projectId = UUID.randomUUID();
        when(gitHubAppIntegrationService.handleSetupCallback(99L, "state-token"))
            .thenReturn(new GitHubAppIntegrationService.SetupCallbackResult(projectId, 99L, true, "result-token"));

        ResponseEntity<Void> response = controller.handleSetup(99L, "state-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
            .contains("/github/access-updated")
            .contains("token=result-token")
            .contains("status=success");
    }

    @Test
    void handleSetup_whenServiceFailsAndStateHasProjectId_redirectsToProjectFailure() {
        UUID projectId = UUID.randomUUID();
        String statePayload = "{\"projectId\":\"" + projectId + "\"}";
        String encodedState = Base64.getUrlEncoder().encodeToString(statePayload.getBytes(StandardCharsets.UTF_8));

        when(gitHubAppIntegrationService.handleSetupCallback(88L, encodedState))
            .thenThrow(new RuntimeException("failed"));

        ResponseEntity<Void> response = controller.handleSetup(88L, encodedState);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
            .contains("/supervisor/projects/" + projectId)
            .contains("githubSetup=failed");
    }

    @Test
    void handleWebhook_delegatesToServiceAndFactory() {
        GitHubWebhookResultDto data = new GitHubWebhookResultDto("installation", "created", 10L, "ACTIVE");
        ResponseEntity<ApiResponse<GitHubWebhookResultDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(gitHubAppIntegrationService.handleWebhook("installation", "sig", "{}")).thenReturn(data);
        when(apiResponseFactory.ok("GitHub webhook processed.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<GitHubWebhookResultDto>> response =
            controller.handleWebhook("installation", "sig", "{}", request);

        assertThat(response).isSameAs(expected);
        verify(gitHubAppIntegrationService).handleWebhook("installation", "sig", "{}");
    }

    @Test
    void validateAccessRequest_delegatesToServiceAndFactory() {
        GitHubAccessRequestValidationDto data = new GitHubAccessRequestValidationDto();
        ResponseEntity<ApiResponse<GitHubAccessRequestValidationDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(gitHubAppIntegrationService.validateProjectAccessRequest("token-1")).thenReturn(data);
        when(apiResponseFactory.ok("GitHub access request is valid.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<GitHubAccessRequestValidationDto>> response =
            controller.validateAccessRequest("token-1", request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void continueAccessRequest_delegatesToServiceAndFactory() {
        GitHubAccessRequestContinueDto data = new GitHubAccessRequestContinueDto(UUID.randomUUID(), "https://github.com");
        ResponseEntity<ApiResponse<GitHubAccessRequestContinueDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(gitHubAppIntegrationService.continueProjectAccessRequest("token-1")).thenReturn(data);
        when(apiResponseFactory.ok("GitHub access request continuation prepared.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<GitHubAccessRequestContinueDto>> response =
            controller.continueAccessRequest("token-1", request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void accessUpdatedEndpoints_delegateToServiceAndFactory() {
        GitHubAccessUpdatedSummaryDto summary = new GitHubAccessUpdatedSummaryDto();
        GitHubAccessUpdatedAcknowledgeDto ack = new GitHubAccessUpdatedAcknowledgeDto(UUID.randomUUID());

        ResponseEntity<ApiResponse<GitHubAccessUpdatedSummaryDto>> summaryExpected = ResponseEntity.ok(new ApiResponse<>());
        ResponseEntity<ApiResponse<GitHubAccessUpdatedAcknowledgeDto>> ackExpected = ResponseEntity.ok(new ApiResponse<>());

        when(gitHubAppIntegrationService.getAccessUpdatedSummary("token-x")).thenReturn(summary);
        when(gitHubAppIntegrationService.acknowledgeAccessUpdated("token-x")).thenReturn(ack);
        when(apiResponseFactory.ok("GitHub access update summary loaded.", summary, request)).thenReturn(summaryExpected);
        when(apiResponseFactory.ok("GitHub access update acknowledged.", ack, request)).thenReturn(ackExpected);

        assertThat(controller.getAccessUpdatedSummary("token-x", request)).isSameAs(summaryExpected);
        assertThat(controller.acknowledgeAccessUpdated("token-x", request)).isSameAs(ackExpected);
    }
}
