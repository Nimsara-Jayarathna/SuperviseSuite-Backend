package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.dto.GitHubAccessSourceDto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.GitHubRepositoryOptionDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessSourceService {

    private static final String GITHUB_REPOSITORY_URL_PATTERN = "^https://github\\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$";
    private static final String INSTALLATION_STATUS_ACTIVE = "ACTIVE";

    private final GitHubIntegrationGuardService guardService;
    private final GitHubAccessSourceRepository accessSourceRepository;
    private final GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;
    private final GitHubAppInstallationRepository gitHubAppInstallationRepository;
    private final GitHubClient gitHubClient;

    public AccessSourceService(
        GitHubIntegrationGuardService guardService,
        GitHubAccessSourceRepository accessSourceRepository,
        GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository,
        GitHubAppInstallationRepository gitHubAppInstallationRepository,
        GitHubClient gitHubClient
    ) {
        this.guardService = guardService;
        this.accessSourceRepository = accessSourceRepository;
        this.gitHubRepositoryEntityRepository = gitHubRepositoryEntityRepository;
        this.gitHubAppInstallationRepository = gitHubAppInstallationRepository;
        this.gitHubClient = gitHubClient;
    }

    @Transactional
    public GitHubAvailableRepositoriesDto createPublicAccessSource(
        String projectIdRaw,
        String authenticatedUserIdRaw,
        String repositoryUrl
    ) {
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");
        UUID projectId = guardService.parseUuid(projectIdRaw, "projectId");
        guardService.requireOwnedProject(projectId, userId);

        String normalizedRepositoryUrl = normalizePublicRepositoryUrl(repositoryUrl);
        ProjectRepositoryMetadataDto metadata = gitHubClient.fetchRepositoryMetadata(normalizedRepositoryUrl, null);

        String ownerLogin = nullable(metadata.getOwnerLogin(), deriveOwnerFromUrl(normalizedRepositoryUrl));
        String repositoryName = nullable(metadata.getName(), deriveRepositoryName(normalizedRepositoryUrl));
        String fullName = ownerLogin + "/" + repositoryName;
        Long githubRepoId = metadata.getExternalRepositoryId() == null
            ? derivedRepositoryId(fullName)
            : metadata.getExternalRepositoryId();

        Instant now = Instant.now();
        GitHubAccessSource source = new GitHubAccessSource();
        source.setProjectId(projectId);
        source.setInstallationId(null);
        source.setOwnerLogin(ownerLogin);
        source.setOwnerType(GitHubIntegrationV2Constants.OWNER_TYPE_USER);
        source.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL);
        source.setCreatedByUserId(userId);
        source.setCreatedAt(now);
        source.setIsActive(true);
        source = accessSourceRepository.save(source);

        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setAccessSourceId(source.getId());
        repository.setGithubRepoId(githubRepoId);
        repository.setFullName(fullName);
        repository.setName(repositoryName);
        repository.setDefaultBranch(nullable(metadata.getDefaultBranch(), "main"));
        repository.setHtmlUrl(nullable(metadata.getUrl(), normalizedRepositoryUrl));
        repository.setOwnerLogin(ownerLogin);
        repository.setCreatedAt(now);
        repository = gitHubRepositoryEntityRepository.save(repository);

        return new GitHubAvailableRepositoriesDto(
            source.getId().toString(),
            List.of(toRepositoryOption(repository)),
            1
        );
    }

    @Transactional
    public GitHubAccessSource createInstallationAccessSource(
        UUID projectId,
        UUID userId,
        Long installationId,
        String flowType
    ) {
        guardService.requireOwnedProject(projectId, userId);

        GitHubAccessSource existing = accessSourceRepository
            .findByProjectIdAndInstallationIdAndIsActiveTrue(projectId, installationId)
            .orElse(null);
        if (existing != null) {
            return existing;
        }

        GitHubAppAuthService.GitHubInstallationContext installationContext = gitHubClient.fetchInstallationContext(installationId);
        upsertInstallationRecord(installationId, installationContext);

        String ownerLogin = nullable(installationContext.accountLogin(), "unknown");
        String ownerTypeRaw = nullable(installationContext.accountType(), "Organization");
        String ownerType = ownerTypeRaw.equalsIgnoreCase("Organization")
            ? GitHubIntegrationV2Constants.OWNER_TYPE_ORG
            : GitHubIntegrationV2Constants.OWNER_TYPE_USER;

        GitHubAccessSource source = new GitHubAccessSource();
        source.setProjectId(projectId);
        source.setInstallationId(installationId);
        source.setOwnerLogin(ownerLogin);
        source.setOwnerType(ownerType);
        source.setAccessType(GitHubIntegrationV2Constants.FLOW_TYPE_INSTALLATION_REQUESTED.equals(flowType)
            ? GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION_REQUESTED
            : GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION_DIRECT);
        source.setCreatedByUserId(userId);
        source.setCreatedAt(Instant.now());
        source.setIsActive(true);
        return accessSourceRepository.save(source);
    }

    private void upsertInstallationRecord(
        Long installationId,
        GitHubAppAuthService.GitHubInstallationContext installationContext
    ) {
        Instant now = Instant.now();

        GitHubAppInstallation installation = gitHubAppInstallationRepository
            .findByInstallationId(installationId)
            .orElseGet(() -> {
                GitHubAppInstallation created = new GitHubAppInstallation();
                created.setInstallationId(installationId);
                created.setCreatedAt(now);
                created.setInstalledAt(now);
                return created;
            });

        installation.setAccountId(installationContext.accountId());
        installation.setAccountLogin(installationContext.accountLogin());
        installation.setAccountType(installationContext.accountType());
        installation.setStatus(INSTALLATION_STATUS_ACTIVE);
        installation.setLastEventAt(now);
        installation.setUpdatedAt(now);
        gitHubAppInstallationRepository.save(installation);
    }

    @Transactional(readOnly = true)
    public List<GitHubAccessSourceDto> getProjectAccessSources(UUID projectId) {
        return accessSourceRepository.findByProjectIdAndIsActiveTrueOrderByCreatedAtDesc(projectId)
            .stream()
            .map(this::toAccessSource)
            .toList();
    }

    private GitHubAccessSourceDto toAccessSource(GitHubAccessSource source) {
        return new GitHubAccessSourceDto(
            source.getId().toString(),
            source.getProjectId().toString(),
            source.getInstallationId(),
            source.getOwnerLogin(),
            source.getOwnerType(),
            source.getAccessType(),
            source.getIsActive(),
            source.getCreatedAt()
        );
    }

    private GitHubRepositoryOptionDto toRepositoryOption(GitHubRepositoryEntity repository) {
        return new GitHubRepositoryOptionDto(
            repository.getId().toString(),
            repository.getGithubRepoId(),
            repository.getFullName(),
            repository.getName(),
            repository.getOwnerLogin(),
            repository.getDefaultBranch(),
            repository.getHtmlUrl()
        );
    }

    private String normalizePublicRepositoryUrl(String repositoryUrl) {
        String normalized = repositoryUrl == null ? null : repositoryUrl.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new ValidationException("repositoryUrl", "Repository URL is required.");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.matches(GITHUB_REPOSITORY_URL_PATTERN)) {
            throw new ValidationException("repositoryUrl", "Repository URL must match https://github.com/{owner}/{repo}.");
        }
        return normalized;
    }

    private String deriveOwnerFromUrl(String repositoryUrl) {
        String[] parts = repositoryUrl.replace("https://github.com/", "").split("/");
        return parts.length > 1 ? parts[0] : "unknown";
    }

    private String deriveRepositoryName(String repositoryUrl) {
        String[] parts = repositoryUrl.replace("https://github.com/", "").split("/");
        return parts.length > 1 ? parts[1] : "repository";
    }

    private long derivedRepositoryId(String fullName) {
        return Integer.toUnsignedLong(fullName.toLowerCase().hashCode());
    }

    private String nullable(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
