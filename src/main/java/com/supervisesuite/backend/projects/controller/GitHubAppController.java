package com.supervisesuite.backend.projects.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/github")
public class GitHubAppController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubAppController.class);

    private final GitHubAppIntegrationService gitHubAppIntegrationService;
    private final ApiResponseFactory apiResponseFactory;
    private final FrontendProperties frontendProperties;
    private final ObjectMapper objectMapper;

    public GitHubAppController(
        GitHubAppIntegrationService gitHubAppIntegrationService,
        ApiResponseFactory apiResponseFactory,
        FrontendProperties frontendProperties,
        ObjectMapper objectMapper
    ) {
        this.gitHubAppIntegrationService = gitHubAppIntegrationService;
        this.apiResponseFactory = apiResponseFactory;
        this.frontendProperties = frontendProperties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/setup")
    public ResponseEntity<Void> handleSetup(
        @RequestParam(name = "installation_id") Long installationId,
        @RequestParam(name = "state", required = false) String state
    ) {
        SetupState legacyState = parseStateSafely(state);
        try {
            GitHubAppIntegrationService.SetupCallbackResult result =
                gitHubAppIntegrationService.handleSetupCallback(installationId, state);
            return redirectTo(buildProjectRedirect(
                result.projectId() == null ? null : result.projectId().toString(),
                "overview",
                "success",
                result.installationId(),
                result.requestFlowCompleted()
            ));
        } catch (Exception exception) {
            LOGGER.warn("GitHub setup callback failed: {}", exception.getMessage(), exception);
            return redirectTo(buildProjectRedirect(legacyState.projectId(), "overview", "failed", null, false));
        }
    }

    @PostMapping("/webhooks")
    public ResponseEntity<ApiResponse<GitHubWebhookResultDto>> handleWebhook(
        @RequestHeader(name = "X-GitHub-Event") String event,
        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature256,
        @RequestBody(required = false) String payload,
        HttpServletRequest request
    ) {
        GitHubWebhookResultDto data = gitHubAppIntegrationService.handleWebhook(event, signature256, payload);
        return apiResponseFactory.ok("GitHub webhook processed.", data, request);
    }

    @GetMapping("/access-requests/validate")
    public ResponseEntity<ApiResponse<GitHubAccessRequestValidationDto>> validateAccessRequest(
        @RequestParam(name = "token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessRequestValidationDto data = gitHubAppIntegrationService.validateProjectAccessRequest(token);
        return apiResponseFactory.ok("GitHub access request is valid.", data, request);
    }

    @PostMapping("/access-requests/continue")
    public ResponseEntity<ApiResponse<GitHubAccessRequestContinueDto>> continueAccessRequest(
        @RequestParam(name = "token") String token,
        HttpServletRequest request
    ) {
        GitHubAccessRequestContinueDto data = gitHubAppIntegrationService.continueProjectAccessRequest(token);
        return apiResponseFactory.ok("GitHub access request continuation prepared.", data, request);
    }

    private SetupState parseStateSafely(String state) {
        if (state == null || state.isBlank()) {
            return new SetupState(null);
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(state);
        } catch (IllegalArgumentException firstFailure) {
            try {
                decoded = Base64.getDecoder().decode(state);
            } catch (IllegalArgumentException secondFailure) {
                LOGGER.warn("GitHub setup callback state could not be decoded as base64.");
                return new SetupState(null);
            }
        }

        try {
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            String projectId = textOrNull(root.path("projectId"));
            return new SetupState(projectId);
        } catch (Exception exception) {
            LOGGER.warn("GitHub setup callback state payload is not valid JSON.");
            return new SetupState(null);
        }
    }

    private URI buildProjectRedirect(
        String projectId,
        String tab,
        String setupStatus,
        Long installationId,
        boolean githubAccessUpdated
    ) {
        String baseUrl = frontendProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("FRONTEND_BASE_URL is not configured.");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl.trim());
        if (projectId != null && !projectId.isBlank()) {
            builder.pathSegment("supervisor", "projects", projectId.trim());
        } else {
            builder.pathSegment("supervisor", "projects");
        }
        builder.queryParam("tab", tab);
        builder.queryParam("githubSetup", setupStatus);
        if (installationId != null && installationId > 0) {
            builder.queryParam("installationId", installationId);
        }
        if (githubAccessUpdated) {
            builder.queryParam("githubAccessUpdated", "true");
        }
        return builder.build(true).toUri();
    }

    private ResponseEntity<Void> redirectTo(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record SetupState(String projectId) {
    }
}
