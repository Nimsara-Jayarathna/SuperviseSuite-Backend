package com.supervisesuite.backend.projects.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.projects.dto.CreateGitHubAccessRequestRequest;
import com.supervisesuite.backend.projects.dto.CreatePublicGitHubAccessSourceRequest;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateV2Dto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallStartDto;
import com.supervisesuite.backend.projects.dto.LinkGitHubRepositoriesRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.dto.StartGitHubAccessSourceInstallRequest;
import com.supervisesuite.backend.projects.dto.UpdateGitHubRepositoryDisplayNameRequest;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.service.githubv2.AccessRequestService;
import com.supervisesuite.backend.projects.service.githubv2.AccessSourceService;
import com.supervisesuite.backend.projects.service.githubv2.GitHubIntegrationV2Constants;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.SetupCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/github")
public class GitHubAccessSourceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubAccessSourceController.class);

    private final ApiResponseFactory apiResponseFactory;
    private final FrontendProperties frontendProperties;
    private final AccessSourceService accessSourceService;
    private final SetupCallbackService setupCallbackService;
    private final AccessRequestService accessRequestService;
    private final RepositoryLinkService repositoryLinkService;

    public GitHubAccessSourceController(
        ApiResponseFactory apiResponseFactory,
        FrontendProperties frontendProperties,
        AccessSourceService accessSourceService,
        SetupCallbackService setupCallbackService,
        AccessRequestService accessRequestService,
        RepositoryLinkService repositoryLinkService
    ) {
        this.apiResponseFactory = apiResponseFactory;
        this.frontendProperties = frontendProperties;
        this.accessSourceService = accessSourceService;
        this.setupCallbackService = setupCallbackService;
        this.accessRequestService = accessRequestService;
        this.repositoryLinkService = repositoryLinkService;
    }

    @PostMapping("/access-source/public")
    public ResponseEntity<ApiResponse<GitHubAvailableRepositoriesDto>> createPublicAccessSource(
        Authentication authentication,
        @Valid @RequestBody CreatePublicGitHubAccessSourceRequest body,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        GitHubAvailableRepositoriesDto data = accessSourceService.createPublicAccessSource(
            body.getProjectId(),
            userId,
            body.getRepositoryUrl()
        );
        return apiResponseFactory.ok("GitHub public access source created.", data, request);
    }

    @PostMapping("/access-source/install/start")
    public ResponseEntity<ApiResponse<GitHubInstallStartDto>> startInstallFlow(
        Authentication authentication,
        @RequestBody(required = false) StartGitHubAccessSourceInstallRequest body,
        HttpServletRequest request
    ) {
        String requestToken = trimToNull(body == null ? null : body.getRequestToken());
        String projectId = trimToNull(body == null ? null : body.getProjectId());

        GitHubInstallStartDto data;
        if (requestToken != null) {
            data = setupCallbackService.startRequestedInstall(requestToken);
        } else {
            if (projectId == null) {
                throw new ValidationException("projectId", "projectId is required.");
            }
            String userId = requireAuthenticatedUserId(authentication);
            data = setupCallbackService.startDirectInstall(projectId, userId);
        }

        return apiResponseFactory.ok("GitHub install flow prepared.", data, request);
    }

    @GetMapping({ "/access-source/callback", "/setup" })
    public ResponseEntity<Void> handleInstallCallback(
        @RequestParam(name = "installation_id") Long installationId,
        @RequestParam(name = "state") String state
    ) {
        try {
            SetupCallbackService.CallbackState callbackState = setupCallbackService.consumeCallbackState(state);
            GitHubAccessSource accessSource = accessSourceService.createInstallationAccessSource(
                callbackState.projectId(),
                callbackState.userId(),
                installationId,
                callbackState.flowType()
            );

            String resultToken = accessRequestService.completeRequest(callbackState.requestId(), installationId);

            URI redirectUri;
            if (resultToken != null) {
                redirectUri = buildAccessUpdatedRedirect(
                    resultToken,
                    "success",
                    callbackState.projectId(),
                    accessSource.getId(),
                    callbackState.flowType()
                );
            } else {
                redirectUri = buildProjectRedirect(
                    callbackState.projectId(),
                    "success",
                    installationId,
                    accessSource.getId(),
                    callbackState.flowType()
                );
            }
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(redirectUri).build();
        } catch (Exception exception) {
            LOGGER.warn("GitHub install callback failed: {}", exception.getMessage(), exception);
            URI redirectUri = buildProjectRedirect(null, "failed", installationId, null, null);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(redirectUri).build();
        }
    }

    @PostMapping("/access-source/request")
    public ResponseEntity<ApiResponse<GitHubAccessRequestCreateV2Dto>> createAccessRequest(
        Authentication authentication,
        @Valid @RequestBody CreateGitHubAccessRequestRequest body,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        GitHubAccessRequestCreateV2Dto data = accessRequestService.createRequest(body.getProjectId(), userId);
        return apiResponseFactory.ok("GitHub access request created.", data, request);
    }

    @DeleteMapping("/access-source/{id}")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> disconnectAccessSource(
        Authentication authentication,
        @PathVariable("id") String sourceId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.disconnectAccessSource(sourceId, userId);
        return apiResponseFactory.ok("GitHub access source disconnected for project.", data, request);
    }

    @GetMapping("/repositories/available")
    public ResponseEntity<ApiResponse<GitHubAvailableRepositoriesDto>> getAvailableRepositories(
        Authentication authentication,
        @RequestParam(name = "sourceId") String sourceId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        GitHubAvailableRepositoriesDto data = repositoryLinkService.getAvailableRepositories(sourceId, userId);
        return apiResponseFactory.ok("Available repositories loaded.", data, request);
    }

    @PostMapping("/repositories/link")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> linkRepositories(
        Authentication authentication,
        @Valid @RequestBody LinkGitHubRepositoriesRequest body,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.linkRepositories(body, userId);
        return apiResponseFactory.ok("GitHub repositories linked.", data, request);
    }

    @DeleteMapping("/repositories/{id}")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> unlinkRepository(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.unlinkRepository(linkedRepositoryId, userId);
        return apiResponseFactory.ok("GitHub repository unlinked.", data, request);
    }

    @PostMapping("/repositories/{id}/enable")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> enableRepository(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.enableRepository(linkedRepositoryId, userId);
        return apiResponseFactory.ok("GitHub repository enabled.", data, request);
    }

    @PostMapping("/repositories/{id}/disable")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> disableRepository(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.disableRepository(linkedRepositoryId, userId);
        return apiResponseFactory.ok("GitHub repository disabled.", data, request);
    }

    @PostMapping("/repositories/{id}/refresh")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> refreshRepository(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.refreshRepository(linkedRepositoryId, userId);
        return apiResponseFactory.ok("GitHub repository refreshed.", data, request);
    }

    @PostMapping("/repositories/{id}/select")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> selectPrimaryRepository(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.selectPrimaryGitHubRepository(linkedRepositoryId, userId);
        return apiResponseFactory.ok("GitHub primary repository updated.", data, request);
    }

    @PostMapping("/repositories/{id}/display-name")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoriesDto>> updateRepositoryDisplayName(
        Authentication authentication,
        @PathVariable("id") String linkedRepositoryId,
        @Valid @RequestBody UpdateGitHubRepositoryDisplayNameRequest body,
        HttpServletRequest request
    ) {
        String userId = requireAuthenticatedUserId(authentication);
        ProjectGitHubRepositoriesDto data = repositoryLinkService.updateRepositoryDisplayName(
            linkedRepositoryId,
            userId,
            body.getCustomName()
        );
        return apiResponseFactory.ok("GitHub repository display name updated.", data, request);
    }

    private URI buildAccessUpdatedRedirect(
        String token,
        String status,
        UUID projectId,
        UUID sourceId,
        String flowType
    ) {
        String baseUrl = trimToNull(frontendProperties.getBaseUrl());
        if (baseUrl == null) {
            throw new IllegalStateException("FRONTEND_BASE_URL is not configured.");
        }

        return UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("github", "access-updated")
            .queryParam("token", token)
            .queryParam("status", status)
            .queryParam("projectId", projectId == null ? null : projectId.toString())
            .queryParam("sourceId", sourceId == null ? null : sourceId.toString())
            .queryParam("flowType", trimToNull(flowType))
            .build(true)
            .toUri();
    }

    private URI buildProjectRedirect(
        UUID projectId,
        String setupStatus,
        Long installationId,
        UUID sourceId,
        String flowType
    ) {
        String baseUrl = trimToNull(frontendProperties.getBaseUrl());
        if (baseUrl == null) {
            throw new IllegalStateException("FRONTEND_BASE_URL is not configured.");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        if (projectId != null) {
            builder.pathSegment("supervisor", "projects", projectId.toString());
        } else {
            builder.pathSegment("supervisor", "projects");
        }

        builder.queryParam("tab", "overview");
        builder.queryParam("githubSetup", nullable(setupStatus, "failed"));

        if (installationId != null && installationId > 0) {
            builder.queryParam("installationId", installationId);
        }

        if (sourceId != null) {
            builder.queryParam("githubSourceId", sourceId.toString());
        }

        String normalizedFlowType = trimToNull(flowType);
        if (normalizedFlowType != null) {
            builder.queryParam("githubFlow", normalizedFlowType);
            if (GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED.equals(normalizedFlowType)) {
                builder.queryParam("githubAccessUpdated", "true");
            }
        }

        return builder.build(true).toUri();
    }

    private String requireAuthenticatedUserId(Authentication authentication) {
        String principalName = authentication == null ? null : trimToNull(authentication.getName());
        if (principalName == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        return principalName;
    }

    private String nullable(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
