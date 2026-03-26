package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.FrontendProperties;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateV2Dto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessRequestV2;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.GitHubAccessRequestV2Repository;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

    private final GitHubAccessRequestV2Repository accessRequestRepository;
    private final ProjectRepository projectRepository;
    private final GitHubAccessSourceRepository accessSourceRepository;
    private final GitHubRepositoryEntityRepository repositoryRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubIntegrationGuardService guardService;
    private final GitHubProperties gitHubProperties;
    private final FrontendProperties frontendProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessRequestService(
        GitHubAccessRequestV2Repository accessRequestRepository,
        ProjectRepository projectRepository,
        GitHubAccessSourceRepository accessSourceRepository,
        GitHubRepositoryEntityRepository repositoryRepository,
        GitHubAppAuthService gitHubAppAuthService,
        GitHubIntegrationGuardService guardService,
        GitHubProperties gitHubProperties,
        FrontendProperties frontendProperties
    ) {
        this.accessRequestRepository = accessRequestRepository;
        this.projectRepository = projectRepository;
        this.accessSourceRepository = accessSourceRepository;
        this.repositoryRepository = repositoryRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.guardService = guardService;
        this.gitHubProperties = gitHubProperties;
        this.frontendProperties = frontendProperties;
    }

    @Transactional
    public GitHubAccessRequestCreateV2Dto createRequest(String projectIdRaw, String authenticatedUserIdRaw) {
        Project project = guardService.requireOwnedProject(projectIdRaw, authenticatedUserIdRaw);
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        Instant now = Instant.now();
        String rawToken = generateOpaqueToken();

        GitHubAccessRequestV2 accessRequest = new GitHubAccessRequestV2();
        accessRequest.setProjectId(project.getId());
        accessRequest.setRequestedByUserId(userId);
        accessRequest.setTokenHash(sha256Base64(rawToken));
        accessRequest.setExpiresAt(now.plusSeconds((long) accessRequestExpiryMinutes() * 60L));
        accessRequest.setCreatedAt(now);
        accessRequest.setUpdatedAt(now);
        accessRequestRepository.save(accessRequest);

        String requestUrl = buildRequestUrl(rawToken);
        return new GitHubAccessRequestCreateV2Dto(project.getId().toString(), requestUrl, accessRequest.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public GitHubAccessRequestV2 requireValidRequestToken(String requestToken) {
        String normalized = trimToNull(requestToken);
        if (normalized == null) {
            throw new ValidationException("token", "Access request token is invalid or expired.");
        }

        GitHubAccessRequestV2 accessRequest = accessRequestRepository
            .findByTokenHash(sha256Base64(normalized))
            .orElseThrow(() -> new ValidationException("token", "Access request token is invalid or expired."));

        Instant now = Instant.now();
        if (accessRequest.getUsedAt() != null || accessRequest.getExpiresAt() == null || now.isAfter(accessRequest.getExpiresAt())) {
            throw new ValidationException("token", "Access request token is invalid or expired.");
        }

        return accessRequest;
    }

    @Transactional
    public String completeRequest(UUID accessRequestId, Long installationId) {
        if (accessRequestId == null) {
            return null;
        }

        return accessRequestRepository.findById(accessRequestId).map(request -> {
            Instant now = Instant.now();
            String resultToken = generateOpaqueToken();

            request.setUsedAt(now);
            request.setInstallationId(installationId);
            request.setResultTokenHash(sha256Base64(resultToken));
            request.setResultExpiresAt(now.plusSeconds((long) accessRequestExpiryMinutes() * 60L));
            request.setResultAcknowledgedAt(null);
            request.setUpdatedAt(now);

            accessRequestRepository.save(request);
            return resultToken;
        }).orElse(null);
    }

    @Transactional
    public GitHubAccessUpdatedSummaryDto getSummary(String resultToken) {
        String normalized = trimToNull(resultToken);
        if (normalized == null) {
            throw new ValidationException("token", "Access request result token is invalid or expired.");
        }

        GitHubAccessRequestV2 accessRequest = accessRequestRepository
            .findByResultTokenHash(sha256Base64(normalized))
            .orElseThrow(() -> new ValidationException("token", "Access request result token is invalid or expired."));

        Instant now = Instant.now();
        if (accessRequest.getUsedAt() == null || accessRequest.getResultExpiresAt() == null || now.isAfter(accessRequest.getResultExpiresAt())) {
            throw new ValidationException("token", "Access request result token is invalid or expired.");
        }

        Project project = projectRepository.findById(accessRequest.getProjectId())
            .orElseThrow(() -> new EntityNotFoundException("Project not found."));

        Long installationId = accessRequest.getInstallationId();
        if (installationId == null) {
            throw new ValidationException("token", "No installation associated with this confirmation.");
        }

        // Fetch repositories from GitHub
        GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context =
            gitHubAppAuthService.fetchInstallationRepositories(installationId, 1, 100);

        List<GitHubInstallationRepositoryDto> repositories = new ArrayList<>();
        for (GitHubAppAuthService.GitHubInstallationRepositoryContext repo : context.repositories()) {
            repositories.add(new GitHubInstallationRepositoryDto(
                repo.repositoryId(),
                repo.repositoryName(),
                repo.fullName(),
                repo.htmlUrl(),
                repo.ownerLogin(),
                repo.defaultBranch() != null ? repo.defaultBranch() : "main"
            ));
        }

        int count = context.totalCount() != null ? context.totalCount().intValue() : repositories.size();
        String scope = count <= 0 ? "NO_REPOSITORIES" : count == 1 ? "SINGLE_REPOSITORY" : "MULTIPLE_REPOSITORIES";
        AccessSourceContext sourceContext = resolveAccessSourceContext(project.getId(), installationId);

        return new GitHubAccessUpdatedSummaryDto(
            project.getId(),
            project.getName(),
            installationId,
            sourceContext.sourceId(),
            sourceContext.flowType(),
            scope,
            count,
            repositories
        );
    }

    @Transactional
    public GitHubAccessUpdatedAcknowledgeDto acknowledge(String resultToken) {
        String normalized = trimToNull(resultToken);
        if (normalized == null) {
            throw new ValidationException("token", "Access request result token is invalid or expired.");
        }

        GitHubAccessRequestV2 accessRequest = accessRequestRepository
            .findByResultTokenHash(sha256Base64(normalized))
            .orElseThrow(() -> new ValidationException("token", "Access request result token is invalid or expired."));

        Instant now = Instant.now();
        accessRequest.setResultAcknowledgedAt(now);
        accessRequestRepository.save(accessRequest);

        return new GitHubAccessUpdatedAcknowledgeDto(accessRequest.getProjectId());
    }

    private int accessRequestExpiryMinutes() {
        GitHubProperties.AccessRequests accessRequests = gitHubProperties.getAccessRequests();
        if (accessRequests == null || accessRequests.getExpiresInMinutes() < 1) {
            return 15;
        }
        return accessRequests.getExpiresInMinutes();
    }

    private String buildRequestUrl(String rawToken) {
        String relative = "/github/request-access?token=" + rawToken;
        String frontendBaseUrl = trimToNull(frontendProperties.getBaseUrl());
        if (frontendBaseUrl == null) {
            return relative;
        }
        if (frontendBaseUrl.endsWith("/")) {
            return frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) + relative;
        }
        return frontendBaseUrl + relative;
    }

    @Transactional(readOnly = true)
    public GitHubAccessUpdatedSummaryDto getPendingSummary(UUID projectId) {
        return accessRequestRepository.findFirstByProjectIdAndUsedAtIsNotNullAndResultAcknowledgedAtIsNullOrderByUsedAtDesc(projectId)
            .map(request -> {
                String projectTitle = projectRepository.findById(request.getProjectId())
                    .map(Project::getName)
                    .orElse("Unknown Project");

                List<GitHubInstallationRepositoryDto> repositories = repositoryRepository.findByAccessSourceInstallationId(request.getInstallationId())
                    .stream()
                    .map(repo -> new GitHubInstallationRepositoryDto(
                        repo.getGithubRepoId(),
                        repo.getName(),
                        repo.getFullName(),
                        repo.getHtmlUrl(),
                        repo.getOwnerLogin(),
                        repo.getDefaultBranch()
                    ))
                    .toList();
                AccessSourceContext sourceContext = resolveAccessSourceContext(request.getProjectId(), request.getInstallationId());

                return new GitHubAccessUpdatedSummaryDto(
                    request.getProjectId(),
                    projectTitle,
                    request.getInstallationId(),
                    sourceContext.sourceId(),
                    sourceContext.flowType(),
                    resolveAccessScope(repositories.size()),
                    repositories.size(),
                    repositories
                );
            })
            .orElse(null);
    }

    @Transactional
    public void acknowledgePending(UUID projectId) {
        accessRequestRepository.findFirstByProjectIdAndUsedAtIsNotNullAndResultAcknowledgedAtIsNullOrderByUsedAtDesc(projectId)
            .ifPresent(request -> {
                request.setResultAcknowledgedAt(Instant.now());
                request.setUpdatedAt(Instant.now());
                accessRequestRepository.save(request);
            });
    }

    private String resolveAccessScope(int count) {
        if (count == 0) return "NO_REPOSITORIES";
        if (count == 1) return "SINGLE_REPOSITORY";
        return "MULTIPLE_REPOSITORIES";
    }

    private AccessSourceContext resolveAccessSourceContext(UUID projectId, Long installationId) {
        if (projectId == null || installationId == null) {
            return new AccessSourceContext(null, GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED);
        }

        GitHubAccessSource source = accessSourceRepository
            .findByProjectIdAndInstallationIdAndIsActiveTrue(projectId, installationId)
            .orElse(null);
        if (source == null) {
            return new AccessSourceContext(null, GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED);
        }

        String flowType = GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION_DIRECT.equals(source.getAccessType())
            ? GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_DIRECT
            : GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED;
        return new AccessSourceContext(source.getId().toString(), flowType);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64(String raw) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record AccessSourceContext(String sourceId, String flowType) {
    }
}
