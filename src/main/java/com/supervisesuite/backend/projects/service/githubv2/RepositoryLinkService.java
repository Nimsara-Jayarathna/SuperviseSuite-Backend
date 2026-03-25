package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessSourceDto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.GitHubRepositoryOptionDto;
import com.supervisesuite.backend.projects.dto.LinkGitHubRepositoriesRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryLinkDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryLinkService {

    private final GitHubIntegrationGuardService guardService;
    private final AccessSourceService accessSourceService;
    private final GitHubAccessSourceRepository accessSourceRepository;
    private final GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;
    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final ProjectRepository projectRepository;
    private final GitHubSyncService gitHubSyncService;
    private final GitHubClient gitHubClient;
    private final GitHubProperties gitHubProperties;

    public RepositoryLinkService(
        GitHubIntegrationGuardService guardService,
        AccessSourceService accessSourceService,
        GitHubAccessSourceRepository accessSourceRepository,
        GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository,
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        ProjectRepository projectRepository,
        GitHubSyncService gitHubSyncService,
        GitHubClient gitHubClient,
        GitHubProperties gitHubProperties
    ) {
        this.guardService = guardService;
        this.accessSourceService = accessSourceService;
        this.accessSourceRepository = accessSourceRepository;
        this.gitHubRepositoryEntityRepository = gitHubRepositoryEntityRepository;
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.projectRepository = projectRepository;
        this.gitHubSyncService = gitHubSyncService;
        this.gitHubClient = gitHubClient;
        this.gitHubProperties = gitHubProperties;
    }

    @Transactional
    public GitHubAvailableRepositoriesDto getAvailableRepositories(String sourceIdRaw, String authenticatedUserIdRaw) {
        UUID sourceId = guardService.parseUuid(sourceIdRaw, "sourceId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        GitHubAccessSource source = accessSourceRepository
            .findByIdAndIsActiveTrue(sourceId)
            .orElseThrow(() -> new ValidationException("sourceId", "GitHub access source not found."));

        guardService.requireOwnedProject(source.getProjectId(), userId);

        if (!GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL.equals(source.getAccessType())) {
            syncInstallationRepositories(source);
        }

        List<GitHubRepositoryOptionDto> repositories = gitHubRepositoryEntityRepository
            .findByAccessSourceIdOrderByFullNameAsc(source.getId())
            .stream()
            .map(this::toRepositoryOption)
            .toList();

        return new GitHubAvailableRepositoriesDto(source.getId().toString(), repositories, repositories.size());
    }

    @Transactional
    public ProjectGitHubRepositoriesDto linkRepositories(
        LinkGitHubRepositoriesRequest request,
        String authenticatedUserIdRaw
    ) {
        UUID projectId = guardService.parseUuid(request.getProjectId(), "projectId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");
        guardService.requireOwnedProject(projectId, userId);

        UUID sourceId = guardService.parseUuid(request.getSourceId(), "sourceId");
        GitHubAccessSource source = accessSourceRepository
            .findByIdAndProjectIdAndIsActiveTrue(sourceId, projectId)
            .orElseThrow(() -> new ValidationException("sourceId", "GitHub access source not found for project."));

        Map<UUID, LinkGitHubRepositoriesRequest.Selection> uniqueSelections = new LinkedHashMap<>();
        for (LinkGitHubRepositoriesRequest.Selection selection : request.getRepositories()) {
            UUID repositoryId = guardService.parseUuid(selection.getGithubRepositoryId(), "githubRepositoryId");
            uniqueSelections.put(repositoryId, selection);
        }

        long existingCount = projectRepositoryLinkRepository.countByProjectId(projectId);
        int maxAllowed = Math.max(1, gitHubProperties.getMaxLinkedReposPerProject());
        if (existingCount + uniqueSelections.size() > maxAllowed) {
            throw new ConflictException("Maximum linked repositories per project exceeded.");
        }

        ProjectRepositoryLink currentPrimary = projectRepositoryLinkRepository
            .findByProjectIdAndIsPrimaryTrue(projectId)
            .orElse(null);
        boolean explicitPrimary = uniqueSelections.values().stream().anyMatch(selection -> Boolean.TRUE.equals(selection.getPrimary()));
        UUID selectedPrimaryLinkId = null;

        int index = 0;
        for (Map.Entry<UUID, LinkGitHubRepositoriesRequest.Selection> entry : uniqueSelections.entrySet()) {
            UUID githubRepositoryId = entry.getKey();
            LinkGitHubRepositoriesRequest.Selection selection = entry.getValue();

            GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository
                .findById(githubRepositoryId)
                .orElseThrow(() -> new ValidationException("githubRepositoryId", "GitHub repository not found."));

            if (!source.getId().equals(repositoryEntity.getAccessSourceId())) {
                throw new ValidationException("githubRepositoryId", "Repository does not belong to the selected access source.");
            }

            if (projectRepositoryLinkRepository.existsByProjectIdAndGithubRepoId(projectId, repositoryEntity.getGithubRepoId())) {
                throw new ConflictException("Repository is already linked to this project.");
            }

            Instant now = Instant.now();
            ProjectRepositoryLink link = new ProjectRepositoryLink();
            link.setProjectId(projectId);
            link.setGithubRepositoryId(repositoryEntity.getId());
            link.setGithubRepoId(repositoryEntity.getGithubRepoId());
            link.setCustomName(trimToNull(selection.getCustomName()));

            boolean isPrimary = explicitPrimary
                ? Boolean.TRUE.equals(selection.getPrimary())
                : currentPrimary == null && index == 0;
            link.setIsPrimary(isPrimary);
            link.setLinkedAt(now);
            link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_PENDING);
            link.setSyncError(null);
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            link = projectRepositoryLinkRepository.save(link);

            if (isPrimary) {
                selectedPrimaryLinkId = link.getId();
            }

            try {
                gitHubSyncService.refreshRepository(link.getId());
            } catch (RuntimeException ignored) {
                // Sync status is persisted in GitHubSyncService.
            }

            index++;
        }

        if (selectedPrimaryLinkId != null) {
            setPrimary(projectId, selectedPrimaryLinkId);
        }
        syncProjectRepositoryUrl(projectId);

        return getProjectRepositories(projectId.toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto unlinkRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));

        guardService.requireOwnedProject(link.getProjectId(), userId);

        UUID projectId = link.getProjectId();
        UUID sourceId = gitHubRepositoryEntityRepository.findById(link.getGithubRepositoryId())
            .map(GitHubRepositoryEntity::getAccessSourceId)
            .orElse(null);
        projectRepositoryLinkRepository.delete(link);
        cleanupAccessSourceIfOrphaned(projectId, sourceId);
        ensureSinglePrimaryRepository(projectId);
        syncProjectRepositoryUrl(projectId);

        return getProjectRepositories(projectId.toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto disconnectAccessSource(
        String sourceIdRaw,
        String authenticatedUserIdRaw
    ) {
        UUID sourceId = guardService.parseUuid(sourceIdRaw, "sourceId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        GitHubAccessSource source = accessSourceRepository
            .findByIdAndIsActiveTrue(sourceId)
            .orElseThrow(() -> new ValidationException("sourceId", "GitHub access source not found."));

        guardService.requireOwnedProject(source.getProjectId(), userId);

        UUID projectId = source.getProjectId();
        accessSourceRepository.delete(source);
        ensureSinglePrimaryRepository(projectId);
        syncProjectRepositoryUrl(projectId);

        return getProjectRepositories(projectId.toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto selectPrimaryRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));

        guardService.requireOwnedProject(link.getProjectId(), userId);

        setPrimary(link.getProjectId(), link.getId());
        syncProjectRepositoryUrl(link.getProjectId());
        return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto refreshRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));
        guardService.requireOwnedProject(link.getProjectId(), userId);

        gitHubSyncService.refreshRepository(linkedRepositoryId);
        return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
    }

    @Transactional(readOnly = true)
    public ProjectGitHubRepositoriesDto getProjectRepositories(String projectIdRaw, String authenticatedUserIdRaw) {
        UUID projectId = guardService.parseUuid(projectIdRaw, "projectId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");
        guardService.requireOwnedProject(projectId, userId);

        List<GitHubAccessSourceDto> sources = accessSourceService.getProjectAccessSources(projectId);
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId);

        Map<UUID, GitHubAccessSourceDto> sourceById = new HashMap<>();
        for (GitHubAccessSourceDto source : sources) {
            sourceById.put(UUID.fromString(source.getId()), source);
        }

        Map<UUID, GitHubRepositoryEntity> repositoryById = new HashMap<>();
        for (ProjectRepositoryLink link : links) {
            gitHubRepositoryEntityRepository.findById(link.getGithubRepositoryId())
                .ifPresent(repository -> repositoryById.put(repository.getId(), repository));
        }

        List<ProjectRepositoryLinkDto> repositoryDtos = links.stream()
            .map(link -> toRepositoryLinkDto(link, repositoryById.get(link.getGithubRepositoryId()), sourceById))
            .toList();

        return new ProjectGitHubRepositoriesDto(
            projectId.toString(),
            Math.max(1, gitHubProperties.getMaxLinkedReposPerProject()),
            sources,
            repositoryDtos
        );
    }

    private void syncInstallationRepositories(GitHubAccessSource source) {
        if (source.getInstallationId() == null || source.getInstallationId() < 1) {
            throw new ValidationException("installationId", "Installation id is required for installation access source.");
        }

        int pageSize = Math.max(1, gitHubProperties.getInstallationRepositories().getMaxPageSize());
        List<GitHubAppAuthService.GitHubInstallationRepositoryContext> repositories = gitHubClient
            .fetchInstallationRepositories(source.getInstallationId(), 10, pageSize);

        Instant now = Instant.now();
        for (GitHubAppAuthService.GitHubInstallationRepositoryContext repository : repositories) {
            if (repository == null || repository.repositoryId() == null) {
                continue;
            }

            GitHubRepositoryEntity entity = gitHubRepositoryEntityRepository
                .findByAccessSourceIdAndGithubRepoId(source.getId(), repository.repositoryId())
                .orElseGet(() -> {
                    GitHubRepositoryEntity created = new GitHubRepositoryEntity();
                    created.setAccessSourceId(source.getId());
                    created.setGithubRepoId(repository.repositoryId());
                    created.setCreatedAt(now);
                    return created;
                });

            entity.setFullName(nullable(repository.fullName(), fallbackFullName(repository.ownerLogin(), repository.repositoryName())));
            entity.setName(nullable(repository.repositoryName(), deriveName(repository.fullName())));
            entity.setOwnerLogin(nullable(repository.ownerLogin(), deriveOwner(repository.fullName())));
            entity.setHtmlUrl(nullable(repository.htmlUrl(), "https://github.com/" + entity.getFullName()));
            entity.setDefaultBranch(nullable(repository.defaultBranch(), "main"));
            gitHubRepositoryEntityRepository.save(entity);
        }
    }

    private void setPrimary(UUID projectId, UUID selectedPrimaryLinkId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId);
        Instant now = Instant.now();
        for (ProjectRepositoryLink link : links) {
            boolean shouldBePrimary = link.getId().equals(selectedPrimaryLinkId);
            if (Boolean.TRUE.equals(link.getIsPrimary()) != shouldBePrimary) {
                link.setIsPrimary(shouldBePrimary);
                link.setUpdatedAt(now);
                projectRepositoryLinkRepository.save(link);
            }
        }
    }

    private void ensureSinglePrimaryRepository(UUID projectId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId);
        if (links.isEmpty()) {
            return;
        }

        ProjectRepositoryLink currentPrimary = links.stream()
            .filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
            .findFirst()
            .orElse(null);

        if (currentPrimary == null) {
            setPrimary(projectId, links.get(0).getId());
            return;
        }

        long primaryCount = links.stream().filter(link -> Boolean.TRUE.equals(link.getIsPrimary())).count();
        if (primaryCount > 1) {
            setPrimary(projectId, currentPrimary.getId());
        }
    }

    private void cleanupAccessSourceIfOrphaned(UUID projectId, UUID sourceId) {
        if (sourceId == null) {
            return;
        }

        List<UUID> sourceRepositoryIds = gitHubRepositoryEntityRepository
            .findByAccessSourceIdOrderByFullNameAsc(sourceId)
            .stream()
            .map(GitHubRepositoryEntity::getId)
            .toList();

        boolean stillLinked = !sourceRepositoryIds.isEmpty() &&
            projectRepositoryLinkRepository.existsByProjectIdAndGithubRepositoryIdIn(projectId, sourceRepositoryIds);

        if (!stillLinked) {
            accessSourceRepository.findByIdAndProjectIdAndIsActiveTrue(sourceId, projectId)
                .ifPresent(accessSourceRepository::delete);
        }
    }

    private void syncProjectRepositoryUrl(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElse(null);
        if (project == null) {
            return;
        }

        String repositoryUrl = null;
        ProjectRepositoryLink primaryLink = projectRepositoryLinkRepository.findByProjectIdAndIsPrimaryTrue(projectId).orElse(null);
        if (primaryLink != null) {
            GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository
                .findById(primaryLink.getGithubRepositoryId())
                .orElse(null);
            if (repositoryEntity != null) {
                repositoryUrl = nullable(repositoryEntity.getHtmlUrl(), null);
                if (repositoryUrl == null && repositoryEntity.getFullName() != null) {
                    repositoryUrl = "https://github.com/" + repositoryEntity.getFullName().trim();
                }
            }
        }

        project.setRepositoryUrl(trimToNull(repositoryUrl));
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
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

    private ProjectRepositoryLinkDto toRepositoryLinkDto(
        ProjectRepositoryLink link,
        GitHubRepositoryEntity repository,
        Map<UUID, GitHubAccessSourceDto> sourceById
    ) {
        GitHubAccessSourceDto source = repository == null ? null : sourceById.get(repository.getAccessSourceId());

        String sourceId = source == null ? null : source.getId();
        String githubRepositoryId = repository == null ? null : repository.getId().toString();

        return new ProjectRepositoryLinkDto(
            link.getId().toString(),
            sourceId,
            githubRepositoryId,
            link.getGithubRepoId(),
            repository == null ? null : repository.getFullName(),
            repository == null ? null : repository.getName(),
            link.getCustomName(),
            repository == null ? null : repository.getOwnerLogin(),
            repository == null ? null : repository.getDefaultBranch(),
            repository == null ? null : repository.getHtmlUrl(),
            link.getIsPrimary(),
            link.getLinkedAt(),
            link.getLastSyncedAt(),
            link.getSyncStatus()
        );
    }

    private String fallbackFullName(String owner, String name) {
        String safeOwner = nullable(owner, "unknown");
        String safeName = nullable(name, "repository");
        return safeOwner + "/" + safeName;
    }

    private String deriveOwner(String fullName) {
        if (fullName == null || !fullName.contains("/")) {
            return "unknown";
        }
        return fullName.substring(0, fullName.indexOf('/'));
    }

    private String deriveName(String fullName) {
        if (fullName == null || !fullName.contains("/")) {
            return "repository";
        }
        return fullName.substring(fullName.indexOf('/') + 1);
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
