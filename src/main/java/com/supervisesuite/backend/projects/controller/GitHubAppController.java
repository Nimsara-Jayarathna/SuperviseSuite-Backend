package com.supervisesuite.backend.projects.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.ApiErrorDetail;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.projects.dto.GitHubWebhookResultDto;
import com.supervisesuite.backend.projects.service.GitHubAppIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
        @RequestParam(name = "state") String state
    ) {
        String projectId = null;
        try {
            SetupState setupState = parseState(state);
            projectId = setupState.projectId();
            if (projectId == null || projectId.isBlank()) {
                throw new ValidationException("state.projectId", "state.projectId is required.");
            }

            gitHubAppIntegrationService.handleSetupCallback(
                installationId,
                setupState.projectId(),
                setupState.repositoryUrl()
            );

            return redirectTo(buildProjectRedirect(projectId, "github", "success"));
        } catch (Exception exception) {
            if (exception instanceof ValidationException validationException) {
                LOGGER.warn(
                    "GitHub setup callback failed: {} details={} rootCause={}",
                    validationException.getMessage(),
                    formatValidationDetails(validationException.getDetails()),
                    rootCauseMessage(validationException)
                );
            } else {
                LOGGER.warn("GitHub setup callback failed: {}", exception.getMessage(), exception);
            }
            return redirectTo(buildProjectRedirect(projectId, "overview", "failed"));
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

    private SetupState parseState(String state) {
        if (state == null || state.isBlank()) {
            throw new ValidationException("state", "state query parameter is required.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(state);
        } catch (IllegalArgumentException firstFailure) {
            try {
                decoded = Base64.getDecoder().decode(state);
            } catch (IllegalArgumentException secondFailure) {
                throw new ValidationException("state", "state must be valid base64 encoded JSON.");
            }
        }

        try {
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            String projectId = textOrNull(root.path("projectId"));
            String repositoryUrl = textOrNull(root.path("repositoryUrl"));
            return new SetupState(projectId, repositoryUrl);
        } catch (Exception exception) {
            throw new ValidationException("state", "state JSON payload is invalid.");
        }
    }

    private URI buildProjectRedirect(String projectId, String tab, String setupStatus) {
        String baseUrl = frontendProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ValidationException("FRONTEND_BASE_URL", "FRONTEND_BASE_URL is not configured.");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl.trim());
        if (projectId != null && !projectId.isBlank()) {
            builder.pathSegment("supervisor", "projects", projectId.trim());
        } else {
            builder.pathSegment("supervisor", "projects");
        }
        builder.queryParam("tab", tab);
        builder.queryParam("githubSetup", setupStatus);
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

    private String formatValidationDetails(List<ApiErrorDetail> details) {
        if (details == null || details.isEmpty()) {
            return "[]";
        }
        return details.stream()
            .map(detail -> {
                String field = detail.getField() == null ? "unknown" : detail.getField();
                String issue = detail.getIssue() == null ? "unknown issue" : detail.getIssue();
                return field + ": " + issue;
            })
            .toList()
            .toString();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "n/a" : current.getMessage();
    }

    private record SetupState(String projectId, String repositoryUrl) {
    }
}
