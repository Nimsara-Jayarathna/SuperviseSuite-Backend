package com.supervisesuite.backend.projects.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.githubv2.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github")
@Tag(name = "GitHub", description = "GitHub App public callbacks, webhooks, and access-request flows.")
public class GitHubAppController {

    private final GitHubAppIntegrationService gitHubAppIntegrationService;
    private final AccessRequestService accessRequestService;
    private final WebhookService webhookService;
    private final ApiResponseFactory apiResponseFactory;

    public GitHubAppController(
        GitHubAppIntegrationService gitHubAppIntegrationService,
        AccessRequestService accessRequestService,
        WebhookService webhookService,
        ApiResponseFactory apiResponseFactory
    ) {
        this.gitHubAppIntegrationService = gitHubAppIntegrationService;
        this.accessRequestService = accessRequestService;
        this.webhookService = webhookService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @PostMapping("/webhooks")
    @Operation(summary = "Handle GitHub webhook (public)")
    public ResponseEntity<ApiResponse<GitHubWebhookResultDto>> handleWebhook(
        @RequestHeader(name = "X-GitHub-Event") String event,
        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature256,
        @RequestBody(required = false) String payload,
        HttpServletRequest request
    ) {
        GitHubWebhookResultDto data = webhookService.handleWebhook(event, signature256, payload);
        return apiResponseFactory.ok("GitHub webhook processed.", data, request);
    }

    @GetMapping("/access-requests/validate")
    @Operation(summary = "Validate a project access request token (public)")
    public ResponseEntity<ApiResponse<GitHubAccessRequestValidationDto>> validateAccessRequest(
        @RequestParam(name = "token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessRequestValidationDto data = gitHubAppIntegrationService.validateProjectAccessRequest(token);
        return apiResponseFactory.ok("GitHub access request is valid.", data, request);
    }

    @PostMapping("/access-requests/continue")
    @Operation(summary = "Continue a project access request flow (public)")
    public ResponseEntity<ApiResponse<GitHubAccessRequestContinueDto>> continueAccessRequest(
        @RequestParam(name = "token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessRequestContinueDto data = gitHubAppIntegrationService.continueProjectAccessRequest(token);
        return apiResponseFactory.ok("GitHub access request continuation prepared.", data, request);
    }

    @GetMapping("/access-updated/summary")
    @Operation(summary = "Fetch access-updated summary (public)")
    public ResponseEntity<ApiResponse<GitHubAccessUpdatedSummaryDto>> getAccessUpdatedSummary(
        @RequestParam(name = "token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessUpdatedSummaryDto data = accessRequestService.getSummary(token);
        return apiResponseFactory.ok("GitHub access update summary loaded.", data, request);
    }

    @PostMapping("/access-updated/acknowledge")
    @Operation(summary = "Acknowledge access-updated result (public)")
    public ResponseEntity<ApiResponse<GitHubAccessUpdatedAcknowledgeDto>> acknowledgeAccessUpdated(
        @RequestParam("token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessUpdatedAcknowledgeDto data = accessRequestService.acknowledge(token);
        return apiResponseFactory.ok("GitHub access update acknowledged.", data, request);
    }

}
