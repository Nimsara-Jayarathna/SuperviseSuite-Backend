package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubAccessSourceDto;
import com.supervisesuite.backend.projects.dto.GitHubAvailableRepositoriesDto;
import com.supervisesuite.backend.projects.dto.GitHubRepositoryOptionDto;
import com.supervisesuite.backend.projects.dto.LinkGitHubRepositoriesRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoriesDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryLinkDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryLinkService {

    private final GitHubIntegrationGuardService guardService;
    private final AccessSourceService accessSourceService;
    private final GitHubAccessSourceRepository accessSourceRepository;
    private final GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;
    private final GitHubAppInstallationRepository gitHubAppInstallationRepository;
    private final ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;
    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository;
    private final ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository;
    private final GitHubSyncService gitHubSyncService;
    private final GitHubClient gitHubClient;
    private final GitHubProperties gitHubProperties;

    public RepositoryLinkService(
        GitHubIntegrationGuardService guardService,
        AccessSourceService accessSourceService,
        GitHubAccessSourceRepository accessSourceRepository,
        GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository,
        GitHubAppInstallationRepository gitHubAppInstallationRepository,
        ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository,
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository,
        ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository,
        GitHubSyncService gitHubSyncService,
        GitHubClient gitHubClient,
        GitHubProperties gitHubProperties
    ) {
        this.guardService = guardService;
        this.accessSourceService = accessSourceService;
        this.accessSourceRepository = accessSourceRepository;
        this.gitHubRepositoryEntityRepository = gitHubRepositoryEntityRepository;
        this.gitHubAppInstallationRepository = gitHubAppInstallationRepository;
        this.projectGitHubInstallationAuthorizationRepository = projectGitHubInstallationAuthorizationRepository;
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.projectRepositoryLinkCommitRepository = projectRepositoryLinkCommitRepository;
        this.projectRepositoryLinkContributorRepository = projectRepositoryLinkContributorRepository;
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

        final Set<Long> visibleRepositoryIds = GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL.equals(source.getAccessType())
            ? null
            : syncInstallationRepositories(source);

        List<GitHubRepositoryOptionDto> repositories = gitHubRepositoryEntityRepository
            .findByAccessSourceIdOrderByFullNameAsc(source.getId())
            .stream()
            .filter(repository -> visibleRepositoryIds == null || visibleRepositoryIds.contains(repository.getGithubRepoId()))
            .map(this::toRepositoryOption)
            .toList();

        int totalCount = repositories.size();
        return new GitHubAvailableRepositoriesDto(source.getId().toString(), repositories, totalCount);
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

        long existingEnabledCount = projectRepositoryLinkRepository.countByProjectIdAndIsEnabledTrue(projectId);
        int maxEnabledAllowed = Math.max(1, gitHubProperties.getMaxEnabledReposPerProject());
        int availableEnableSlots = Math.max(0, maxEnabledAllowed - (int) existingEnabledCount);

        ProjectRepositoryLink currentPrimary = projectRepositoryLinkRepository
            .findByProjectIdAndIsPrimaryTrueAndIsEnabledTrue(projectId)
            .orElse(null);
        UUID selectedPrimaryLinkId = null;

        int enabledAllocated = 0;
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

            boolean enableThisLink = enabledAllocated < availableEnableSlots;
            if (enableThisLink) {
                enabledAllocated++;
            }

            boolean requestedAsPrimary = Boolean.TRUE.equals(selection.getPrimary());
            link.setIsPrimary(false);
            link.setIsEnabled(enableThisLink);
            link.setLinkedAt(now);
            link.setSyncStatus(enableThisLink
                ? GitHubIntegrationV2Constants.SYNC_STATUS_PENDING
                : GitHubIntegrationV2Constants.SYNC_STATUS_DISABLED);
            link.setSyncError(null);
            link.setAccessType(source.getAccessType());
            link.setRepositoryUrl(repositoryEntity.getHtmlUrl());
            link.setRepositoryName(repositoryEntity.getFullName());
            link.setGithubInstallationId(source.getInstallationId());
            link.setDefaultBranch(repositoryEntity.getDefaultBranch());
            link.setLinkedBySupervisorUserId(userId);

            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            link = projectRepositoryLinkRepository.save(link);

            if (enableThisLink && requestedAsPrimary) {
                selectedPrimaryLinkId = link.getId();
            }

            if (enableThisLink) {
                try {
                    gitHubSyncService.refreshRepository(link.getId());
                } catch (RuntimeException ignored) {
                    // Sync status is persisted in GitHubSyncService.
                }
            }

        }
        if (selectedPrimaryLinkId != null) {
            setPrimary(projectId, selectedPrimaryLinkId);
        } else if (currentPrimary == null) {
            ensureSinglePrimaryRepository(projectId);
        }
        pruneUnlinkedRepositoryInventoryForSource(source);
        consumeSingleUseInstallationSourceIfApplicable(source);

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

        return getProjectRepositories(projectId.toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto enableRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));
        guardService.requireOwnedProject(link.getProjectId(), userId);

        if (Boolean.TRUE.equals(link.getIsEnabled())) {
            return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
        }

        long enabledCount = projectRepositoryLinkRepository.countByProjectIdAndIsEnabledTrue(link.getProjectId());
        int maxEnabledAllowed = Math.max(1, gitHubProperties.getMaxEnabledReposPerProject());
        if (enabledCount >= maxEnabledAllowed) {
            throw new ConflictException("Maximum enabled repositories per project exceeded.");
        }

        Instant now = Instant.now();
        link.setIsEnabled(true);
        link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_PENDING);
        link.setSyncError(null);
        link.setUpdatedAt(now);
        projectRepositoryLinkRepository.save(link);

        ProjectRepositoryLink currentPrimary = projectRepositoryLinkRepository
            .findByProjectIdAndIsPrimaryTrueAndIsEnabledTrue(link.getProjectId())
            .orElse(null);
        if (currentPrimary == null) {
            setPrimary(link.getProjectId(), link.getId());
        }

        try {
            gitHubSyncService.refreshRepository(link.getId());
        } catch (RuntimeException ignored) {
            // Sync status is persisted in GitHubSyncService.
        }

        return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto disableRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));
        guardService.requireOwnedProject(link.getProjectId(), userId);

        if (!Boolean.TRUE.equals(link.getIsEnabled())) {
            return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
        }

        Instant now = Instant.now();
        link.setIsEnabled(false);
        link.setIsPrimary(false);
        link.setLastSyncedAt(null);
        link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_DISABLED);
        link.setSyncError(null);
        link.setUpdatedAt(now);
        projectRepositoryLinkRepository.save(link);

        projectRepositoryLinkContributorRepository.deleteByProjectRepositoryLinkId(link.getId());

        ensureSinglePrimaryRepository(link.getProjectId());

        return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto disconnectAccessSource(
        String sourceIdRaw,
        String authenticatedUserIdRaw
    ) {
        UUID sourceId = guardService.parseUuid(sourceIdRaw, "sourceId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        GitHubAccessSource source = accessSourceRepository
            .findById(sourceId)
            .orElseThrow(() -> new ValidationException("sourceId", "GitHub access source not found."));

        guardService.requireOwnedProject(source.getProjectId(), userId);

        UUID projectId = source.getProjectId();
        
        // Delete all repository links associated with this access source for this project
        List<UUID> repositoryIds = gitHubRepositoryEntityRepository.findByAccessSourceIdOrderByFullNameAsc(source.getId())
            .stream()
            .map(GitHubRepositoryEntity::getId)
            .toList();
        if (!repositoryIds.isEmpty()) {
            projectRepositoryLinkRepository.deleteByProjectIdAndGithubRepositoryIdIn(projectId, repositoryIds);
        }
        
        accessSourceRepository.delete(source);
        ensureSinglePrimaryRepository(projectId);

        return getProjectRepositories(projectId.toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto selectPrimaryGitHubRepository(String linkedRepositoryIdRaw, String authenticatedUserIdRaw) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));

        guardService.requireOwnedProject(link.getProjectId(), userId);
        if (!Boolean.TRUE.equals(link.getIsEnabled())) {
            throw new ConflictException("Disabled repositories cannot be selected as primary.");
        }

        setPrimary(link.getProjectId(), link.getId());
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
        if (!Boolean.TRUE.equals(link.getIsEnabled())) {
            throw new ConflictException("Disabled repositories cannot be refreshed.");
        }

        gitHubSyncService.refreshRepository(linkedRepositoryId);
        return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
    }

    @Transactional
    public ProjectGitHubRepositoriesDto updateRepositoryDisplayName(
        String linkedRepositoryIdRaw,
        String authenticatedUserIdRaw,
        String customNameRaw
    ) {
        UUID linkedRepositoryId = guardService.parseUuid(linkedRepositoryIdRaw, "repositoryId");
        UUID userId = guardService.parseUuid(authenticatedUserIdRaw, "authenticatedUserId");

        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkedRepositoryId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository not found."));
        guardService.requireOwnedProject(link.getProjectId(), userId);

        String normalizedCustomName = trimToNull(customNameRaw);
        if (normalizedCustomName != null && normalizedCustomName.length() > 255) {
            throw new ValidationException("customName", "Display name must not exceed 255 characters.");
        }

        if ((link.getCustomName() == null && normalizedCustomName == null)
            || (link.getCustomName() != null && link.getCustomName().equals(normalizedCustomName))) {
            return getProjectRepositories(link.getProjectId().toString(), authenticatedUserIdRaw);
        }

        link.setCustomName(normalizedCustomName);
        link.setUpdatedAt(Instant.now());
        projectRepositoryLinkRepository.save(link);

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
            Math.max(1, gitHubProperties.getMaxEnabledReposPerProject()),
            sources,
            repositoryDtos
        );
    }

    private Set<Long> syncInstallationRepositories(GitHubAccessSource source) {
        if (source.getInstallationId() == null || source.getInstallationId() < 1) {
            throw new ValidationException("installationId", "Installation id is required for installation access source.");
        }

        int pageSize = Math.max(1, gitHubProperties.getInstallationRepositories().getMaxPageSize());
        int page = 1;
        Long totalCount = null;
        Set<Long> visibleRepositoryIds = new HashSet<>();

        while (true) {
            GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context = gitHubClient
                .fetchInstallationRepositoriesPage(source.getInstallationId(), page, pageSize);

            Instant now = Instant.now();
            for (GitHubAppAuthService.GitHubInstallationRepositoryContext repository : context.repositories()) {
                if (repository == null || repository.repositoryId() == null) {
                    continue;
                }
                visibleRepositoryIds.add(repository.repositoryId());

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
                entity.setDefaultBranch(nullable(repository.defaultBranch(), defaultBranch()));
                gitHubRepositoryEntityRepository.save(entity);
            }

            if (context.totalCount() != null) {
                totalCount = context.totalCount();
            }

            int returnedCount = context.repositories().size();
            boolean hasNext = totalCount != null
                ? (long) page * pageSize < totalCount
                : returnedCount >= pageSize && returnedCount > 0;
            if (!hasNext) {
                break;
            }
            page++;
        }

        return visibleRepositoryIds;
    }

    private void setPrimary(UUID projectId, UUID selectedPrimaryLinkId) {
        Instant now = Instant.now();

        // 1. Demote any current primary links for this project (except the one to be promoted)
        List<ProjectRepositoryLink> currentPrimaries = projectRepositoryLinkRepository.findByProjectIdAndIsPrimaryTrue(projectId)
            .stream()
            .filter(link -> !link.getId().equals(selectedPrimaryLinkId))
            .toList();

        if (!currentPrimaries.isEmpty()) {
            for (ProjectRepositoryLink link : currentPrimaries) {
                link.setIsPrimary(false);
                link.setUpdatedAt(now);
                projectRepositoryLinkRepository.save(link);
            }
            // Flush to ensure demotions are registered in DB before any new promotion violates the unique constraint
            projectRepositoryLinkRepository.flush();
        }

        // 2. Promote the selected link to primary
        projectRepositoryLinkRepository.findById(selectedPrimaryLinkId).ifPresent(link -> {
            if (!Boolean.TRUE.equals(link.getIsPrimary())) {
                link.setIsPrimary(true);
                link.setUpdatedAt(now);
                projectRepositoryLinkRepository.save(link);
            }
        });
    }

    private void ensureSinglePrimaryRepository(UUID projectId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId);
        if (links.isEmpty()) {
            return;
        }

        List<ProjectRepositoryLink> enabledLinks = links.stream()
            .filter(link -> Boolean.TRUE.equals(link.getIsEnabled()))
            .toList();

        if (enabledLinks.isEmpty()) {
            Instant now = Instant.now();
            for (ProjectRepositoryLink link : links) {
                if (Boolean.TRUE.equals(link.getIsPrimary())) {
                    link.setIsPrimary(false);
                    link.setUpdatedAt(now);
                    projectRepositoryLinkRepository.save(link);
                }
            }
            return;
        }

        ProjectRepositoryLink currentPrimary = enabledLinks.stream()
            .filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
            .findFirst()
            .orElse(null);

        if (currentPrimary == null) {
            setPrimary(projectId, enabledLinks.get(0).getId());
            return;
        }

        long primaryCount = enabledLinks.stream().filter(link -> Boolean.TRUE.equals(link.getIsPrimary())).count();
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
            accessSourceRepository.findById(sourceId)
                .filter(source -> projectId.equals(source.getProjectId()))
                .ifPresent(accessSourceRepository::delete);
        }
    }

    private void consumeSingleUseInstallationSourceIfApplicable(GitHubAccessSource source) {
        if (source == null) {
            return;
        }
        if (!Boolean.TRUE.equals(source.getIsActive())) {
            return;
        }
        String accessType = source.getAccessType();
        boolean singleUseInstallationSource =
            GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION_REQUESTED.equals(accessType) ||
            GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION_DIRECT.equals(accessType);
        if (!singleUseInstallationSource) {
            return;
        }

        source.setIsActive(false);
        source.setUpdatedAt(Instant.now());
        accessSourceRepository.save(source);
    }

    private void pruneUnlinkedRepositoryInventoryForSource(GitHubAccessSource source) {
        if (source == null || source.getId() == null || source.getProjectId() == null) {
            return;
        }

        List<GitHubRepositoryEntity> sourceRepositories =
            gitHubRepositoryEntityRepository.findByAccessSourceIdOrderByFullNameAsc(source.getId());
        if (sourceRepositories.isEmpty()) {
            return;
        }

        Set<UUID> linkedRepositoryEntityIds = projectRepositoryLinkRepository
            .findByProjectIdOrderByCreatedAtAsc(source.getProjectId())
            .stream()
            .map(ProjectRepositoryLink::getGithubRepositoryId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        List<UUID> staleRepositoryEntityIds = sourceRepositories.stream()
            .map(GitHubRepositoryEntity::getId)
            .filter(id -> !linkedRepositoryEntityIds.contains(id))
            .toList();

        if (!staleRepositoryEntityIds.isEmpty()) {
            gitHubRepositoryEntityRepository.deleteAllByIdInBatch(staleRepositoryEntityIds);
        }
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
        String resolvedSourceId = repository == null ? sourceId : repository.getAccessSourceId().toString();
        String githubRepositoryId = repository == null ? null : repository.getId().toString();

        return new ProjectRepositoryLinkDto(
            link.getId().toString(),
            resolvedSourceId,
            githubRepositoryId,
            link.getGithubRepoId(),
            repository == null ? null : repository.getFullName(),
            repository == null ? null : repository.getName(),
            link.getCustomName(),
            repository == null ? null : repository.getOwnerLogin(),
            repository == null ? null : repository.getDefaultBranch(),
            repository == null ? null : repository.getHtmlUrl(),
            link.getAccessType(),
            link.getIsPrimary(),
            link.getIsEnabled(),
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

    private String defaultBranch() {
        String configured = trimToNull(gitHubProperties.getDefaultBranch());
        return configured == null ? GitHubIntegrationV2Constants.DEFAULT_BRANCH : configured;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public GitHubAppAuthService.GitHubInstallationRepositoriesPageContext fetchInstallationRepositories(
        Long installationId,
        int page,
        int size
    ) {
        return gitHubClient.fetchInstallationRepositoriesPage(installationId, page, size);
    }

    @Transactional
    public ProjectRepositoryLink linkRepository(
        UUID projectId,
        Long installationId,
        Long repositoryId,
        UUID supervisorUserId
    ) {
        GitHubAccessSource source = accessSourceRepository.findByProjectIdAndInstallationIdAndIsActiveTrue(projectId, installationId)
            .orElseGet(() -> {
                GitHubAccessSource newSource = new GitHubAccessSource();
                newSource.setProjectId(projectId);
                newSource.setInstallationId(installationId);
                newSource.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION);
                newSource.setIsActive(true);
                newSource.setCreatedAt(Instant.now());
                newSource.setUpdatedAt(Instant.now());
                return accessSourceRepository.save(newSource);
            });

        GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository.findByAccessSourceIdAndGithubRepoId(source.getId(), repositoryId)
            .orElseGet(() -> {
                GitHubRepositoryEntity newEntity = new GitHubRepositoryEntity();
                newEntity.setAccessSourceId(source.getId());
                newEntity.setGithubRepoId(repositoryId);
                newEntity.setCreatedAt(Instant.now());
                return gitHubRepositoryEntityRepository.save(newEntity);
            });

        ProjectRepositoryLink link = projectRepositoryLinkRepository.findByProjectIdAndGithubRepositoryId(projectId, repositoryEntity.getId())
            .orElseGet(() -> {
                ProjectRepositoryLink newLink = new ProjectRepositoryLink();
                newLink.setProjectId(projectId);
                newLink.setGithubRepositoryId(repositoryEntity.getId());
                newLink.setGithubRepoId(repositoryId);
                newLink.setGithubInstallationId(installationId);
                newLink.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_INSTALLATION);
                newLink.setRepositoryUrl(repositoryEntity.getHtmlUrl());
                newLink.setRepositoryName(repositoryEntity.getFullName());
                newLink.setDefaultBranch(repositoryEntity.getDefaultBranch());
                newLink.setLinkedBySupervisorUserId(supervisorUserId);
                newLink.setIsEnabled(true);
                newLink.setLinkedAt(Instant.now());
                newLink.setCreatedAt(Instant.now());
                return newLink;
            });

        link.setUpdatedAt(Instant.now());
        return projectRepositoryLinkRepository.save(link);
    }

    @Transactional
    public ProjectRepositoryLink linkRepositoryByUrl(UUID projectId, String repositoryUrl, UUID supervisorUserId) {
        GitHubAccessSource source = accessSourceRepository.findByProjectIdAndAccessTypeAndIsActiveTrue(projectId, GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL)
            .orElseGet(() -> {
                GitHubAccessSource newSource = new GitHubAccessSource();
                newSource.setProjectId(projectId);
                newSource.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL);
                newSource.setIsActive(true);
                newSource.setCreatedAt(Instant.now());
                newSource.setUpdatedAt(Instant.now());
                return accessSourceRepository.save(newSource);
            });

        GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository.findByAccessSourceIdAndHtmlUrl(source.getId(), repositoryUrl)
            .orElseGet(() -> {
                GitHubRepositoryEntity newEntity = new GitHubRepositoryEntity();
                newEntity.setAccessSourceId(source.getId());
                newEntity.setHtmlUrl(repositoryUrl);
                newEntity.setCreatedAt(Instant.now());
                return gitHubRepositoryEntityRepository.save(newEntity);
            });

        ProjectRepositoryLink link = projectRepositoryLinkRepository.findByProjectIdAndGithubRepoId(projectId, repositoryEntity.getGithubRepoId())
            .orElseGet(() -> {
                ProjectRepositoryLink newLink = new ProjectRepositoryLink();
                newLink.setProjectId(projectId);
                newLink.setGithubRepositoryId(repositoryEntity.getId());
                newLink.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL);
                newLink.setRepositoryUrl(repositoryUrl);
                newLink.setRepositoryName(deriveName(repositoryUrl)); // Fallback if entity is fresh
                newLink.setLinkedBySupervisorUserId(supervisorUserId);
                newLink.setIsEnabled(true);
                newLink.setLinkedAt(Instant.now());
                newLink.setCreatedAt(Instant.now());
                return newLink;
            });

        projectRepositoryLinkRepository.save(link);
        setPrimary(projectId, link.getId());
        return link;
    }

    @Transactional
    public void disconnectAllLinks(UUID projectId) {
        projectRepositoryLinkRepository.deleteByProjectId(projectId);
    }

    @Transactional
    public void linkManualRepository(UUID projectId, String repositoryUrl, UUID supervisorUserId) {
        disconnectAllLinks(projectId);
        String[] repositoryParts = extractRepositoryParts(repositoryUrl);
        String ownerLogin = repositoryParts[0];
        String repositoryName = repositoryParts[1];
        String fullRepositoryName = ownerLogin + "/" + repositoryName;

        GitHubAccessSource source = accessSourceRepository
            .findByProjectIdAndAccessTypeAndIsActiveTrue(projectId, GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL)
            .orElseGet(() -> {
                GitHubAccessSource created = new GitHubAccessSource();
                created.setProjectId(projectId);
                created.setOwnerLogin(ownerLogin);
                created.setOwnerType(GitHubIntegrationV2Constants.OWNER_TYPE_USER);
                created.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL);
                created.setCreatedByUserId(supervisorUserId);
                created.setIsActive(true);
                created.setCreatedAt(Instant.now());
                created.setUpdatedAt(Instant.now());
                return accessSourceRepository.save(created);
            });

        long syntheticRepoId = toSyntheticRepoId(repositoryUrl);
        GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository
            .findByAccessSourceIdAndHtmlUrl(source.getId(), repositoryUrl)
            .orElseGet(() -> {
                GitHubRepositoryEntity created = new GitHubRepositoryEntity();
                created.setAccessSourceId(source.getId());
                created.setGithubRepoId(syntheticRepoId);
                created.setHtmlUrl(repositoryUrl);
                created.setName(repositoryName);
                created.setFullName(fullRepositoryName);
                created.setOwnerLogin(ownerLogin);
                created.setDefaultBranch("main");
                created.setCreatedAt(Instant.now());
                return gitHubRepositoryEntityRepository.save(created);
            });

        ProjectRepositoryLink link = new ProjectRepositoryLink();
        link.setProjectId(projectId);
        link.setGithubRepositoryId(repositoryEntity.getId());
        link.setGithubRepoId(repositoryEntity.getGithubRepoId());
        link.setRepositoryUrl(repositoryUrl);
        link.setRepositoryName(repositoryName);
        link.setCreatedAt(Instant.now());
        link.setUpdatedAt(Instant.now());
        link.setLinkedAt(Instant.now());
        link.setIsPrimary(true);
        link.setIsEnabled(true);
        link.setAccessType(GitHubIntegrationV2Constants.ACCESS_TYPE_PUBLIC_URL);
        link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_PENDING);
        
        projectRepositoryLinkRepository.save(link);
    }

    private long toSyntheticRepoId(String repositoryUrl) {
        UUID stable = UUID.nameUUIDFromBytes(repositoryUrl.getBytes(StandardCharsets.UTF_8));
        long candidate = stable.getMostSignificantBits() & Long.MAX_VALUE;
        return candidate == 0L ? 1L : candidate;
    }

    private String[] extractRepositoryParts(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl);
            String path = uri.getPath();
            if (path != null) {
                String[] tokens = path.split("/");
                if (tokens.length >= 3 && !tokens[1].isBlank() && !tokens[2].isBlank()) {
                    return new String[] {tokens[1], tokens[2]};
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to safe defaults for non-URI values.
        }
        return new String[] {"manual", deriveName(repositoryUrl)};
    }

    @Transactional
    public void disconnectRepository(UUID linkId) {
        projectRepositoryLinkRepository.findById(linkId).ifPresent(link -> {
            projectRepositoryLinkCommitRepository.deleteByProjectRepositoryLinkId(link.getId());
            projectRepositoryLinkContributorRepository.deleteByProjectRepositoryLinkId(link.getId());
            projectRepositoryLinkRepository.delete(link);
        });
    }

    public ProjectGitHubAccessMetadata resolveLink(UUID projectId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByLinkedAtDesc(projectId);
        
        // Find installation ID from active sources if available
        Long installationId = accessSourceRepository.findByProjectIdAndIsActiveTrueOrderByCreatedAtDesc(projectId)
            .stream()
            .filter(source -> source.getInstallationId() != null)
            .map(GitHubAccessSource::getInstallationId)
            .findFirst()
            .orElse(null);

        if (links.isEmpty()) {
            return new ProjectGitHubAccessMetadata(
                installationId,
                0,
                installationId != null ? "installation" : "none",
                null
            );
        }

        ProjectRepositoryLink primary = links.stream()
            .filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
            .findFirst()
            .orElse(links.get(0));

        return new ProjectGitHubAccessMetadata(
            installationId != null ? installationId : primary.getGithubInstallationId(),
            links.size(),
            installationId != null || primary.getGithubInstallationId() != null ? "installation" : "public",
            primary.getRepositoryUrl()
        );
    }

    @Transactional
    public void disconnectAllLinksByInstallationId(Long installationId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByGithubInstallationId(installationId);
        for (ProjectRepositoryLink link : links) {
            projectRepositoryLinkCommitRepository.deleteByProjectRepositoryLinkId(link.getId());
            projectRepositoryLinkContributorRepository.deleteByProjectRepositoryLinkId(link.getId());
        }
        projectRepositoryLinkRepository.deleteByGithubInstallationId(installationId);
    }
}
