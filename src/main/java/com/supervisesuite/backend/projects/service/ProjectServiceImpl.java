package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.entity.GitHubAppInstallation;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.entity.ProjectRepository;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryCommit;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryContributor;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import com.supervisesuite.backend.projects.integration.github.GitHubInstallationDisconnectedException;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryContributorRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectServiceImpl implements ProjectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServiceImpl.class);
    private static final int DEFAULT_PAGE = 1;
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_IDLE = "idle";
    private static final String PROVIDER_GITHUB = "github";

    private final GitHubCommitClient gitHubCommitClient;
    private final ProjectGitHubDashboardMapper dashboardMapper;
    private final ProjectRepositoryCacheRepository projectRepositoryCacheRepository;
    private final ProjectRepositoryCommitRepository projectRepositoryCommitRepository;
    private final ProjectRepositoryContributorRepository projectRepositoryContributorRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubAppInstallationRepository gitHubAppInstallationRepository;
    private final ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;
    private final com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository;
    private final GitHubProperties gitHubProperties;

    ProjectServiceImpl(
        GitHubCommitClient gitHubCommitClient,
        ProjectGitHubDashboardMapper dashboardMapper,
        ProjectRepositoryCacheRepository projectRepositoryCacheRepository,
        ProjectRepositoryCommitRepository projectRepositoryCommitRepository,
        ProjectRepositoryContributorRepository projectRepositoryContributorRepository,
        GitHubAppAuthService gitHubAppAuthService,
        GitHubAppInstallationRepository gitHubAppInstallationRepository,
        ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository,
        com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository,
        GitHubProperties gitHubProperties
    ) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.dashboardMapper = dashboardMapper;
        this.projectRepositoryCacheRepository = projectRepositoryCacheRepository;
        this.projectRepositoryCommitRepository = projectRepositoryCommitRepository;
        this.projectRepositoryContributorRepository = projectRepositoryContributorRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubAppInstallationRepository = gitHubAppInstallationRepository;
        this.projectGitHubInstallationAuthorizationRepository = projectGitHubInstallationAuthorizationRepository;
        this.projectRepository = projectRepository;
        this.gitHubProperties = gitHubProperties;
    }

    @Override
    public ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return dashboardMapper.noRepository();
        }

        ProjectRepositoryMetadataDto metadata = null;
        List<ProjectCommitDto> commits = List.of();

        try {
            metadata = gitHubCommitClient.fetchRepositoryMetadata(repositoryUrl);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to fetch repository metadata for {}", repositoryUrl, exception);
        }

        try {
            commits = gitHubCommitClient.fetchRecentCommits(repositoryUrl);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to fetch recent commits for {}", repositoryUrl, exception);
        }

        try {
            return dashboardMapper.toDashboard(repositoryUrl, metadata, commits, Instant.now());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to build GitHub dashboard payload for {}", repositoryUrl, exception);
            return dashboardMapper.toDashboard(repositoryUrl, metadata, List.of(), Instant.now());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubDashboardDto getGitHubDashboard(UUID projectId, String repositoryUrl) {
        ProjectGitHubPreviewDto preview = getGitHubPreview(projectId, repositoryUrl);
        if (!preview.isRepositoryLinked() || preview.getRepositories() == null || preview.getRepositories().isEmpty()) {
            return dashboardMapper.noRepository();
        }

        ProjectGitHubPreviewDto.RepositoryItem repositoryItem = preview.getRepositories().get(0);
        ProjectGitHubDashboardDto.Repository repository = new ProjectGitHubDashboardDto.Repository(
            nullable(repositoryItem.getName(), deriveRepositoryName(repositoryItem.getUrl())),
            repositoryItem.getUrl(),
            nullable(repositoryItem.getDefaultBranch(), defaultBranch())
        );

        List<ProjectGitHubDashboardDto.Contributor> contributors = preview.getContributorsPreview().stream()
            .map(contributor -> new ProjectGitHubDashboardDto.Contributor(
                contributor.getName(),
                contributor.getCommitCount()
            ))
            .toList();

        List<ProjectGitHubDashboardDto.RecentCommit> recentCommits = preview.getRecentCommitsPreview().stream()
            .map(commit -> new ProjectGitHubDashboardDto.RecentCommit(
                commit.getSha(),
                commit.getMessage(),
                commit.getAuthor(),
                commit.getCommittedAt()
            ))
            .toList();

        ProjectGitHubPreviewDto.ActivitySummary summary = preview.getActivitySummary();
        ProjectGitHubDashboardDto.ActivitySummary activitySummary = new ProjectGitHubDashboardDto.ActivitySummary(
            summary == null ? 0 : summary.getTotalCommits(),
            summary == null ? null : summary.getLastActivityAt(),
            summary == null ? STATUS_IDLE : nullable(summary.getStatus(), STATUS_IDLE)
        );

        return new ProjectGitHubDashboardDto(
            true,
            repository,
            activitySummary,
            contributors,
            recentCommits
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPreviewDto getGitHubPreview(UUID projectId, String repositoryUrl) {
        ProjectRepository repository = resolveLinkedRepository(projectId, repositoryUrl);
        if (repository == null || repository.getRepositoryUrl() == null || repository.getRepositoryUrl().isBlank()) {
            return new ProjectGitHubPreviewDto(
                false,
                List.of(),
                new ProjectGitHubPreviewDto.ActivitySummary(0, null, STATUS_IDLE),
                List.of(),
                List.of()
            );
        }

        long totalCommits = repository.getId() == null ? 0 : projectRepositoryCommitRepository.countByRepositoryId(repository.getId());
        Instant lastActivityAt = repository.getId() == null
            ? null
            : projectRepositoryCommitRepository.findTopByRepositoryIdOrderByCommittedAtDesc(repository.getId())
                .map(ProjectRepositoryCommit::getCommittedAt)
                .orElse(null);

        List<ProjectGitHubPreviewDto.ContributorPreviewItem> contributorsPreview = repository.getId() == null
            ? List.of()
            : projectRepositoryContributorRepository
                .findByRepositoryIdOrderByCommitCountDescContributorNameAsc(
                    repository.getId(),
                    PageRequest.of(0, previewContributorsLimit())
                )
                .getContent()
                .stream()
                .map(contributor -> new ProjectGitHubPreviewDto.ContributorPreviewItem(
                    contributor.getContributorName(),
                    contributor.getCommitCount()
                ))
                .toList();

        List<ProjectGitHubPreviewDto.RecentCommitPreviewItem> recentCommitsPreview = repository.getId() == null
            ? List.of()
            : projectRepositoryCommitRepository
                .findTop10ByRepositoryIdOrderByCommittedAtDesc(repository.getId())
                .stream()
                .limit(previewCommitsLimit())
                .map(commit -> new ProjectGitHubPreviewDto.RecentCommitPreviewItem(
                    commit.getSha(),
                    commit.getMessage(),
                    commit.getAuthor(),
                    commit.getCommittedAt(),
                    commit.getCommitType()
                ))
                .toList();

        ProjectGitHubPreviewDto.RepositoryItem repositoryItem = new ProjectGitHubPreviewDto.RepositoryItem(
            repository.getId() == null ? null : repository.getId().toString(),
            nullable(repository.getRepositoryName(), deriveRepositoryName(repository.getRepositoryUrl())),
            repository.getRepositoryUrl(),
            nullable(repository.getDefaultBranch(), defaultBranch()),
            repository.getLastSyncedAt()
        );

        return new ProjectGitHubPreviewDto(
            true,
            List.of(repositoryItem),
            new ProjectGitHubPreviewDto.ActivitySummary(
                Math.toIntExact(Math.min(Integer.MAX_VALUE, totalCommits)),
                lastActivityAt,
                resolveStatus(lastActivityAt, Instant.now())
            ),
            contributorsPreview,
            recentCommitsPreview
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getGitHubActivityPage(
        UUID projectId,
        String repositoryUrl,
        int page,
        int size
    ) {
        ProjectRepository repository = resolveLinkedRepository(projectId, repositoryUrl);
        if (repository == null || repository.getId() == null) {
            int normalizedPage = normalizePage(page);
            int normalizedSize = normalizePageSize(size);
            return new ProjectGitHubPageDto<>(List.of(), normalizedPage, normalizedSize, 0, false);
        }

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "committedAt"));
        Page<ProjectRepositoryCommit> data = projectRepositoryCommitRepository
            .findByRepositoryIdOrderByCommittedAtDesc(repository.getId(), pageable);

        List<ProjectGitHubDashboardDto.RecentCommit> items = data.getContent().stream()
            .map(commit -> new ProjectGitHubDashboardDto.RecentCommit(
                commit.getSha(),
                commit.getMessage(),
                commit.getAuthor(),
                commit.getCommittedAt()
            ))
            .toList();

        return new ProjectGitHubPageDto<>(
            items,
            normalizedPage,
            normalizedSize,
            data.getTotalElements(),
            data.hasNext()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getGitHubContributorsPage(
        UUID projectId,
        String repositoryUrl,
        int page,
        int size
    ) {
        ProjectRepository repository = resolveLinkedRepository(projectId, repositoryUrl);
        if (repository == null || repository.getId() == null) {
            int normalizedPage = normalizePage(page);
            int normalizedSize = normalizePageSize(size);
            return new ProjectGitHubPageDto<>(List.of(), normalizedPage, normalizedSize, 0, false);
        }

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(
            normalizedPage - 1,
            normalizedSize,
            Sort.by(Sort.Order.desc("commitCount"), Sort.Order.asc("contributorName"))
        );
        Page<ProjectRepositoryContributor> data = projectRepositoryContributorRepository
            .findByRepositoryIdOrderByCommitCountDescContributorNameAsc(repository.getId(), pageable);

        List<ProjectGitHubDashboardDto.Contributor> items = data.getContent().stream()
            .map(contributor -> new ProjectGitHubDashboardDto.Contributor(
                contributor.getContributorName(),
                contributor.getCommitCount()
            ))
            .toList();

        return new ProjectGitHubPageDto<>(
            items,
            normalizedPage,
            normalizedSize,
            data.getTotalElements(),
            data.hasNext()
        );
    }

    @Override
    @Transactional(noRollbackFor = GitHubInstallationDisconnectedException.class)
    public void refreshGitHubData(UUID projectId, String repositoryUrl) {
        ProjectRepository linkedRepository = resolveLinkedRepository(projectId, repositoryUrl);
        if (linkedRepository == null || linkedRepository.getRepositoryUrl() == null || linkedRepository.getRepositoryUrl().isBlank()) {
            throw new ValidationException("repositoryUrl", "No repository linked for this project.");
        }
        String targetRepositoryUrl = linkedRepository.getRepositoryUrl().trim();

        Instant now = Instant.now();
        ProjectRepository repository = ensurePrimaryRepository(projectId, targetRepositoryUrl, now);
        repository = projectRepositoryCacheRepository.findById(repository.getId())
            .orElseThrow(() -> new ValidationException("repositoryUrl", "No repository linked for this project."));

        try {
            ProjectRepositoryMetadataDto metadata = gitHubCommitClient
                .fetchRepositoryMetadata(targetRepositoryUrl, repository.getInstallationId());
            List<ProjectCommitDto> commits = gitHubCommitClient
                .fetchRecentCommits(targetRepositoryUrl, repository.getInstallationId());

            repository.setRepositoryName(nullable(metadata.getName(), deriveRepositoryName(targetRepositoryUrl)));
            repository.setRepositoryUrl(nullable(metadata.getUrl(), targetRepositoryUrl));
            repository.setRepositoryExternalId(metadata.getExternalRepositoryId());
            repository.setOwnerLogin(nullable(metadata.getOwnerLogin(), repository.getOwnerLogin()));
            repository.setDefaultBranch(nullable(metadata.getDefaultBranch(), defaultBranch()));
            repository.setLastSyncedAt(now);
            repository.setSyncStatus("SUCCESS");
            repository.setLastSyncError(null);
            repository.setUpdatedAt(now);
            repository = projectRepositoryCacheRepository.save(repository);

            syncCommits(repository.getId(), commits, now);
            syncContributors(repository.getId(), commits, now);
        } catch (DomainException exception) {
            String failureMessage = nullable(exception.getMessage(), "GitHub refresh failed.");
            if (exception instanceof GitHubInstallationDisconnectedException) {
                unlinkProjectAfterInstallationDisconnect(projectId, now);
                LOGGER.warn(
                    "GitHub installation access removed for projectId={} repositoryUrl={}. Cleared linked repository and cached GitHub data.",
                    projectId,
                    targetRepositoryUrl
                );
                throw new GitHubInstallationDisconnectedException(
                    failureMessage + " Linked repository and cached GitHub data were removed for this project.",
                    exception
                );
            }

            repository.setLastSyncedAt(now);
            repository.setSyncStatus("FAILED");
            repository.setLastSyncError(failureMessage);
            repository.setUpdatedAt(now);
            projectRepositoryCacheRepository.save(repository);

            LOGGER.warn(
                "GitHub refresh failed for projectId={} repositoryUrl={}: {} (code={} status={})",
                projectId,
                targetRepositoryUrl,
                failureMessage,
                exception.getCode(),
                exception.getStatus()
            );
            throw exception;
        } catch (RuntimeException exception) {
            String failureMessage = nullable(exception.getMessage(), "GitHub refresh failed.");
            repository.setLastSyncedAt(now);
            repository.setSyncStatus("FAILED");
            repository.setLastSyncError(failureMessage);
            repository.setUpdatedAt(now);
            projectRepositoryCacheRepository.save(repository);

            Throwable rootCause = rootCause(exception);
            LOGGER.warn(
                "GitHub refresh failed for projectId={} repositoryUrl={}: {} (cause={} message={})",
                projectId,
                targetRepositoryUrl,
                failureMessage,
                rootCause == null ? "n/a" : rootCause.getClass().getSimpleName(),
                rootCause == null ? "n/a" : nullable(rootCause.getMessage(), "n/a")
            );
            throw new ServiceUnavailableException(failureMessage, exception);
        }
    }

    private void unlinkProjectAfterInstallationDisconnect(UUID projectId, Instant now) {
        clearGitHubLinkage(projectId);
        projectRepository.findByIdAndDeletedAtIsNull(projectId).ifPresent(project -> {
            project.setRepositoryUrl(null);
            project.setUpdatedAt(now);
            project.setLastActivityAt(now);
            projectRepository.save(project);
        });
    }

    @Override
    @Transactional
    public void linkGitHubInstallation(
        UUID projectId,
        String repositoryUrl,
        Long installationId,
        String ownerLogin
    ) {
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        if (normalizedUrl == null) {
            throw new ValidationException("repositoryUrl", "Repository URL is required to link installation.");
        }
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "GitHub installation id is required.");
        }

        Instant now = Instant.now();
        ProjectRepository repository = ensurePrimaryRepository(projectId, normalizedUrl, now);
        repository.setInstallationId(installationId);
        repository.setOwnerLogin(nullable(ownerLogin, repository.getOwnerLogin()));
        repository.setLinkedBySupervisorUserId(null);
        repository.setLinkedAt(null);
        repository.setUpdatedAt(now);
        projectRepositoryCacheRepository.save(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public GitHubInstallationRepositoryPageDto getInstallationRepositories(
        UUID projectId,
        Long installationId,
        UUID supervisorUserId,
        int page,
        Integer size
    ) {
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }
        int normalizedPage = normalizeInstallationRepositoriesPage(page);
        int normalizedSize = normalizeInstallationRepositoriesPageSize(size);
        requireUsableInstallation(installationId);
        requireProjectInstallationAuthorization(projectId, installationId, supervisorUserId);

        GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context =
            gitHubAppAuthService.fetchInstallationRepositories(installationId, normalizedPage, normalizedSize);

        List<GitHubInstallationRepositoryDto> items = context.repositories().stream()
            .filter(repository -> repository != null)
            .map(repository -> {
                String fullName = resolveFullName(repository);
                return new GitHubInstallationRepositoryDto(
                    repository.repositoryId(),
                    nullable(repository.repositoryName(), deriveRepositoryName(repository.htmlUrl())),
                    fullName,
                    resolveRepositoryUrlFromContext(repository),
                    nullable(repository.ownerLogin(), deriveOwnerFromFullName(fullName)),
                    nullable(repository.defaultBranch(), defaultBranch())
                );
            })
            .toList();

        int returnedCount = items.size();
        Long totalCount = context.totalCount();
        boolean hasPrevious = normalizedPage > 1;
        boolean hasNext = totalCount != null
            ? (long) normalizedPage * normalizedSize < totalCount
            : returnedCount >= normalizedSize && returnedCount > 0;
        Integer nextPage = hasNext ? normalizedPage + 1 : null;

        LOGGER.info(
            "GitHub installation repositories loaded installationId={} page={} size={} returnedCount={} totalCount={}",
            installationId,
            normalizedPage,
            normalizedSize,
            returnedCount,
            totalCount
        );

        return new GitHubInstallationRepositoryPageDto(
            items,
            normalizedPage,
            normalizedSize,
            returnedCount,
            totalCount,
            hasNext,
            hasPrevious,
            nextPage
        );
    }

    @Override
    @Transactional
    public ProjectGitHubRepositoryLinkDto linkProjectToInstallationRepository(
        UUID projectId,
        Long installationId,
        Long repositoryId,
        UUID supervisorUserId
    ) {
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }

        requireUsableInstallation(installationId);
        requireProjectInstallationAuthorization(projectId, installationId, supervisorUserId);
        if (repositoryId == null || repositoryId < 1) {
            throw new ValidationException("repositoryId", "GitHub repository id is required.");
        }

        GitHubAppAuthService.GitHubInstallationRepositoryContext selectedRepository =
            resolveInstallationRepositoryById(installationId, repositoryId);

        String repositoryUrl = resolveRepositoryUrlFromContext(selectedRepository);

        Instant now = Instant.now();
        String fullName = resolveFullName(selectedRepository);
        ProjectRepository repository = ensurePrimaryRepository(projectId, repositoryUrl, now);
        repository.setInstallationId(installationId);
        repository.setRepositoryExternalId(repositoryId);
        repository.setRepositoryName(nullable(selectedRepository.repositoryName(), deriveRepositoryName(repositoryUrl)));
        repository.setOwnerLogin(nullable(selectedRepository.ownerLogin(), deriveOwnerFromFullName(fullName)));
        repository.setDefaultBranch(nullable(selectedRepository.defaultBranch(), defaultBranch()));
        repository.setLinkedBySupervisorUserId(supervisorUserId);
        repository.setLinkedAt(now);
        repository.setSyncStatus(null);
        repository.setLastSyncError(null);
        repository.setUpdatedAt(now);
        repository = projectRepositoryCacheRepository.save(repository);

        try {
            refreshGitHubData(projectId, repositoryUrl);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "GitHub refresh after link failed for projectId={} repositoryId={} installationId={}: {}",
                projectId,
                repositoryId,
                installationId,
                nullable(exception.getMessage(), "refresh failed")
            );
        }

        ProjectRepository refreshedRepository = repository.getId() == null
            ? repository
            : projectRepositoryCacheRepository.findById(repository.getId()).orElse(repository);

        return new ProjectGitHubRepositoryLinkDto(
            projectId,
            refreshedRepository.getInstallationId(),
            refreshedRepository.getRepositoryExternalId(),
            refreshedRepository.getRepositoryName(),
            resolveRepositoryFullName(
                refreshedRepository.getOwnerLogin(),
                refreshedRepository.getRepositoryName(),
                fullName
            ),
            refreshedRepository.getRepositoryUrl(),
            refreshedRepository.getOwnerLogin(),
            refreshedRepository.getDefaultBranch(),
            refreshedRepository.getLastSyncedAt()
        );
    }

    private GitHubAppAuthService.GitHubInstallationRepositoryContext resolveInstallationRepositoryById(
        Long installationId,
        Long repositoryId
    ) {
        int page = 1;
        int pageSize = installationRepositoriesMaxPageSize();

        while (true) {
            GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context =
                gitHubAppAuthService.fetchInstallationRepositories(installationId, page, pageSize);

            GitHubAppAuthService.GitHubInstallationRepositoryContext found = context.repositories().stream()
                .filter(repository -> repository != null && repositoryId.equals(repository.repositoryId()))
                .findFirst()
                .orElse(null);
            if (found != null) {
                return found;
            }

            int returnedCount = context.repositories().size();
            Long totalCount = context.totalCount();
            boolean hasNext = totalCount != null
                ? (long) page * pageSize < totalCount
                : returnedCount >= pageSize && returnedCount > 0;
            if (!hasNext) {
                break;
            }
            page++;
        }

        throw new ValidationException(
            "repositoryId",
            "Selected repository is not accessible under the selected installation."
        );
    }

    @Override
    @Transactional
    public void switchToManualRepository(UUID projectId, String repositoryUrl) {
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        if (normalizedUrl == null) {
            throw new ValidationException("repositoryUrl", "Repository URL is required.");
        }

        Instant now = Instant.now();
        ProjectRepository target = ensurePrimaryRepository(projectId, normalizedUrl, now);
        List<ProjectRepository> repositories = projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId);

        java.util.Set<Long> removedInstallationIds = new java.util.HashSet<>();
        for (ProjectRepository repository : repositories) {
            if (repository.getId() == null || repository.getId().equals(target.getId())) {
                continue;
            }
            if (repository.getInstallationId() != null) {
                removedInstallationIds.add(repository.getInstallationId());
            }
            purgeRepositoryData(repository.getId());
        }

        List<ProjectRepository> toDelete = repositories.stream()
            .filter(repository -> repository.getId() != null && !repository.getId().equals(target.getId()))
            .toList();
        if (!toDelete.isEmpty()) {
            projectRepositoryCacheRepository.deleteAll(toDelete);
        }

        if (target.getInstallationId() != null) {
            removedInstallationIds.add(target.getInstallationId());
        }
        target.setInstallationId(null);
        target.setRepositoryExternalId(null);
        target.setOwnerLogin(null);
        target.setRepositoryName(nullable(target.getRepositoryName(), deriveRepositoryName(normalizedUrl)));
        target.setDefaultBranch(null);
        target.setLinkedBySupervisorUserId(null);
        target.setLinkedAt(null);
        target.setSyncStatus(null);
        target.setLastSyncError(null);
        target.setUpdatedAt(now);
        projectRepositoryCacheRepository.save(target);
        projectGitHubInstallationAuthorizationRepository.deleteByProjectId(projectId);

        cleanupOrphanInstallations(removedInstallationIds);
    }

    @Override
    @Transactional
    public void clearGitHubLinkage(UUID projectId) {
        List<ProjectRepository> repositories = projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        if (repositories.isEmpty()) {
            return;
        }

        java.util.Set<Long> removedInstallationIds = new java.util.HashSet<>();
        for (ProjectRepository repository : repositories) {
            if (repository.getInstallationId() != null) {
                removedInstallationIds.add(repository.getInstallationId());
            }
            if (repository.getId() != null) {
                purgeRepositoryData(repository.getId());
            }
        }

        projectRepositoryCacheRepository.deleteAll(repositories);
        projectGitHubInstallationAuthorizationRepository.deleteByProjectId(projectId);
        cleanupOrphanInstallations(removedInstallationIds);
    }

    private void syncCommits(UUID repositoryId, List<ProjectCommitDto> commits, Instant now) {
        projectRepositoryCommitRepository.deleteByRepositoryId(repositoryId);
        projectRepositoryCommitRepository.flush();

        Map<String, ProjectRepositoryCommit> uniqueBySha = new LinkedHashMap<>();
        (commits == null ? List.<ProjectCommitDto>of() : commits)
            .stream()
            .filter(commit -> commit != null)
            .forEach(commit -> {
                ProjectRepositoryCommit entity = new ProjectRepositoryCommit();
                entity.setRepositoryId(repositoryId);
                entity.setSha(nullable(commit.getSha(), "unknown"));
                entity.setMessage(nullable(commit.getMessage(), ""));
                entity.setAuthor(nullable(commit.getAuthor(), "Unknown"));
                entity.setCommittedAt(commit.getCommittedAt());
                entity.setCommitType(resolveCommitType(commit.getMessage()));
                entity.setCreatedAt(now);
                uniqueBySha.putIfAbsent(entity.getSha(), entity);
            });

        List<ProjectRepositoryCommit> entities = List.copyOf(uniqueBySha.values());

        if (!entities.isEmpty()) {
            projectRepositoryCommitRepository.saveAll(entities);
        }
    }

    private void syncContributors(UUID repositoryId, List<ProjectCommitDto> commits, Instant now) {
        projectRepositoryContributorRepository.deleteByRepositoryId(repositoryId);
        projectRepositoryContributorRepository.flush();

        Map<String, Integer> countByContributor = new HashMap<>();
        Map<String, Instant> lastByContributor = new HashMap<>();

        for (ProjectCommitDto commit : commits == null ? List.<ProjectCommitDto>of() : commits) {
            if (commit == null) {
                continue;
            }
            String contributor = nullable(commit.getAuthor(), "Unknown");
            countByContributor.put(contributor, countByContributor.getOrDefault(contributor, 0) + 1);

            Instant committedAt = commit.getCommittedAt();
            if (committedAt != null) {
                Instant existing = lastByContributor.get(contributor);
                if (existing == null || committedAt.isAfter(existing)) {
                    lastByContributor.put(contributor, committedAt);
                }
            }
        }

        List<ProjectRepositoryContributor> entities = countByContributor.entrySet().stream()
            .map(entry -> {
                ProjectRepositoryContributor contributor = new ProjectRepositoryContributor();
                contributor.setRepositoryId(repositoryId);
                contributor.setContributorName(entry.getKey());
                contributor.setCommitCount(entry.getValue());
                contributor.setLastContributionAt(lastByContributor.get(entry.getKey()));
                contributor.setUpdatedAt(now);
                return contributor;
            })
            .sorted(Comparator
                .comparing(ProjectRepositoryContributor::getCommitCount, Comparator.reverseOrder())
                .thenComparing(ProjectRepositoryContributor::getContributorName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (!entities.isEmpty()) {
            projectRepositoryContributorRepository.saveAll(entities);
        }
    }

    private void purgeRepositoryData(UUID repositoryId) {
        projectRepositoryCommitRepository.deleteByRepositoryId(repositoryId);
        projectRepositoryCommitRepository.flush();
        projectRepositoryContributorRepository.deleteByRepositoryId(repositoryId);
        projectRepositoryContributorRepository.flush();
    }

    private void cleanupOrphanInstallations(java.util.Set<Long> installationIds) {
        if (installationIds == null || installationIds.isEmpty()) {
            return;
        }
        for (Long installationId : installationIds) {
            if (installationId == null) {
                continue;
            }
            boolean stillReferenced = !projectRepositoryCacheRepository.findByInstallationId(installationId).isEmpty();
            if (!stillReferenced) {
                gitHubAppInstallationRepository.findByInstallationId(installationId)
                    .ifPresent(gitHubAppInstallationRepository::delete);
            }
        }
    }

    private ProjectGitHubInstallationAuthorization requireProjectInstallationAuthorization(
        UUID projectId,
        Long installationId,
        UUID supervisorUserId
    ) {
        ProjectGitHubInstallationAuthorization authorization = projectGitHubInstallationAuthorizationRepository
            .findByProjectIdAndInstallationId(projectId, installationId)
            .orElseThrow(() -> new ValidationException(
                "installationId",
                "This installation is not authorized for this project. Connect GitHub App from this project first."
            ));

        if (supervisorUserId != null && !supervisorUserId.equals(authorization.getAuthorizedBySupervisorUserId())) {
            throw new ValidationException(
                "installationId",
                "This installation authorization was created by a different supervisor."
            );
        }

        return authorization;
    }

    private GitHubAppInstallation requireUsableInstallation(Long installationId) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "GitHub installation id is required.");
        }

        GitHubAppInstallation installation = gitHubAppInstallationRepository
            .findByInstallationId(installationId)
            .orElseThrow(() -> new ValidationException(
                "installationId",
                "GitHub installation not found. Connect GitHub App first."
            ));

        String status = trimToNull(installation.getStatus());
        if (status == null) {
            throw new ValidationException(
                "installationId",
                "GitHub installation is not ready yet. Reconnect GitHub App and try again."
            );
        }

        String normalizedStatus = status.toUpperCase(Locale.ROOT);
        if ("DELETED".equals(normalizedStatus) || "SUSPENDED".equals(normalizedStatus)) {
            throw new ValidationException(
                "installationId",
                "GitHub installation is not active. Reconnect GitHub App to continue."
            );
        }

        return installation;
    }

    private String resolveRepositoryUrlFromContext(GitHubAppAuthService.GitHubInstallationRepositoryContext repository) {
        String htmlUrl = trimToNull(repository == null ? null : repository.htmlUrl());
        if (htmlUrl != null) {
            return htmlUrl;
        }

        String fullName = resolveFullName(repository);
        if (fullName != null && fullName.contains("/")) {
            return "https://github.com/" + fullName;
        }

        throw new ValidationException(
            "repositoryId",
            "Selected repository does not include a valid URL."
        );
    }

    private String resolveFullName(GitHubAppAuthService.GitHubInstallationRepositoryContext repository) {
        if (repository == null) {
            return null;
        }

        String fullName = trimToNull(repository.fullName());
        if (fullName != null) {
            return fullName;
        }

        String ownerLogin = trimToNull(repository.ownerLogin());
        String repositoryName = trimToNull(repository.repositoryName());
        if (ownerLogin != null && repositoryName != null) {
            return ownerLogin + "/" + repositoryName;
        }

        return repositoryName;
    }

    private String resolveRepositoryFullName(String ownerLogin, String repositoryName, String fallback) {
        String owner = trimToNull(ownerLogin);
        String name = trimToNull(repositoryName);
        if (owner != null && name != null) {
            return owner + "/" + name;
        }
        return trimToNull(fallback);
    }

    private String deriveOwnerFromFullName(String fullName) {
        String normalized = trimToNull(fullName);
        if (normalized == null || !normalized.contains("/")) {
            return null;
        }
        String owner = normalized.substring(0, normalized.indexOf('/')).trim();
        return owner.isEmpty() ? null : owner;
    }

    private ProjectRepository resolvePrimaryRepository(UUID projectId, String repositoryUrl) {
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        if (normalizedUrl == null) {
            return null;
        }

        ProjectRepository matchingRepository = projectRepositoryCacheRepository
            .findByProjectIdAndProviderAndRepositoryUrl(projectId, PROVIDER_GITHUB, normalizedUrl)
            .orElse(null);
        if (matchingRepository != null) {
            return matchingRepository;
        }

        return buildTransientRepository(projectId, normalizedUrl);
    }

    private ProjectRepository resolveLinkedRepository(UUID projectId, String repositoryUrl) {
        ProjectRepository byUrl = resolvePrimaryRepository(projectId, repositoryUrl);
        if (byUrl != null && byUrl.getRepositoryUrl() != null && !byUrl.getRepositoryUrl().isBlank()) {
            return byUrl;
        }

        ProjectRepository primary = projectRepositoryCacheRepository
            .findByProjectIdAndIsPrimaryTrue(projectId)
            .orElse(null);
        if (primary != null && PROVIDER_GITHUB.equalsIgnoreCase(nullable(primary.getProvider(), ""))) {
            return primary;
        }

        return projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
            .filter(repository -> PROVIDER_GITHUB.equalsIgnoreCase(nullable(repository.getProvider(), "")))
            .findFirst()
            .orElse(null);
    }

    private ProjectRepository ensurePrimaryRepository(UUID projectId, String repositoryUrl, Instant now) {
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        if (normalizedUrl == null) {
            throw new ValidationException("repositoryUrl", "No repository linked for this project.");
        }

        ProjectRepository repository = projectRepositoryCacheRepository
            .findByProjectIdAndProviderAndRepositoryUrl(projectId, PROVIDER_GITHUB, normalizedUrl)
            .orElseGet(() -> {
                ProjectRepository created = new ProjectRepository();
                created.setProjectId(projectId);
                created.setProvider(PROVIDER_GITHUB);
                created.setRepositoryUrl(normalizedUrl);
                created.setRepositoryName(deriveRepositoryName(normalizedUrl));
                created.setDefaultBranch(defaultBranch());
                created.setIsPrimary(true);
                created.setCreatedAt(now);
                return created;
            });

        List<ProjectRepository> repositories = projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        for (ProjectRepository existing : repositories) {
            if (existing.getId() != null && !existing.getId().equals(repository.getId())) {
                existing.setIsPrimary(false);
                existing.setUpdatedAt(now);
            }
        }

        repository.setProjectId(projectId);
        repository.setProvider(PROVIDER_GITHUB);
        repository.setRepositoryUrl(normalizedUrl);
        repository.setIsPrimary(true);
        if (repository.getCreatedAt() == null) {
            repository.setCreatedAt(now);
        }
        repository.setUpdatedAt(now);
        ProjectRepository saved = projectRepositoryCacheRepository.save(repository);
        if (!repositories.isEmpty()) {
            projectRepositoryCacheRepository.saveAll(
                repositories.stream()
                    .filter(existing -> existing.getId() != null && !existing.getId().equals(saved.getId()))
                    .toList()
            );
        }
        return saved;
    }

    private ProjectRepository buildTransientRepository(UUID projectId, String repositoryUrl) {
        ProjectRepository repository = new ProjectRepository();
        repository.setProjectId(projectId);
        repository.setProvider(PROVIDER_GITHUB);
        repository.setRepositoryUrl(repositoryUrl);
        repository.setRepositoryName(deriveRepositoryName(repositoryUrl));
        repository.setDefaultBranch(defaultBranch());
        repository.setIsPrimary(true);
        return repository;
    }

    private String resolveStatus(Instant lastActivityAt, Instant now) {
        if (lastActivityAt == null || now == null) {
            return STATUS_IDLE;
        }
        return lastActivityAt.isAfter(now.minus(activeWindowDuration())) ? STATUS_ACTIVE : STATUS_IDLE;
    }

    private String resolveCommitType(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("merge")) {
            return "merge";
        }
        if (normalized.startsWith("feat") || normalized.startsWith("feature")) {
            return "feat";
        }
        if (normalized.startsWith("fix")) {
            return "fix";
        }
        return null;
    }

    private String deriveRepositoryName(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return "repository";
        }

        try {
            URI uri = URI.create(repositoryUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "repository";
            }

            String[] parts = path.split("/");
            String candidate = parts[parts.length - 1].isBlank() && parts.length > 1
                ? parts[parts.length - 2]
                : parts[parts.length - 1];
            if (candidate == null || candidate.isBlank()) {
                return "repository";
            }
            return stripGitSuffix(candidate.trim());
        } catch (IllegalArgumentException exception) {
            String[] parts = repositoryUrl.split("/");
            if (parts.length == 0) {
                return "repository";
            }
            return stripGitSuffix(parts[parts.length - 1]);
        }
    }

    private String stripGitSuffix(String value) {
        if (value == null) {
            return "repository";
        }
        String normalized = value.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".git")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private String nullable(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private int normalizePage(int page) {
        return page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeInstallationRepositoriesPage(int page) {
        if (page < 1) {
            throw new ValidationException("page", "Page must be greater than zero.");
        }
        return page;
    }

    private int normalizeInstallationRepositoriesPageSize(Integer size) {
        int configuredDefault = installationRepositoriesDefaultPageSize();
        int configuredMax = installationRepositoriesMaxPageSize();

        if (size == null) {
            return configuredDefault;
        }
        if (size < 1) {
            throw new ValidationException("size", "Size must be greater than zero.");
        }
        return Math.min(size, configuredMax);
    }

    private int installationRepositoriesDefaultPageSize() {
        GitHubProperties.InstallationRepositories configuration = gitHubProperties.getInstallationRepositories();
        if (configuration == null) {
            throw new ValidationException(
                "app.github.installation-repositories.default-page-size",
                "GitHub installation repositories pagination config is missing."
            );
        }
        int configuredDefault = configuration.getDefaultPageSize();
        return Math.max(1, configuredDefault);
    }

    private int installationRepositoriesMaxPageSize() {
        GitHubProperties.InstallationRepositories configuration = gitHubProperties.getInstallationRepositories();
        if (configuration == null) {
            throw new ValidationException(
                "app.github.installation-repositories.max-page-size",
                "GitHub installation repositories pagination config is missing."
            );
        }
        int configuredDefault = installationRepositoriesDefaultPageSize();
        int configuredMax = configuration.getMaxPageSize();
        return Math.max(configuredDefault, configuredMax);
    }

    private String normalizeRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        return repositoryUrl.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private int normalizePageSize(int size) {
        int configuredDefault = Math.max(1, gitHubProperties.getDefaultPageSize());
        int configuredMax = Math.max(configuredDefault, gitHubProperties.getMaxPageSize());

        if (size < 1) {
            return configuredDefault;
        }
        return Math.min(size, configuredMax);
    }

    private Duration activeWindowDuration() {
        return Duration.ofHours(Math.max(1, gitHubProperties.getActivityActiveWindowHours()));
    }

    private int previewCommitsLimit() {
        return Math.max(1, gitHubProperties.getPreviewCommitsLimit());
    }

    private int previewContributorsLimit() {
        return Math.max(1, gitHubProperties.getPreviewContributorsLimit());
    }

    private String defaultBranch() {
        String configured = gitHubProperties.getDefaultBranch();
        if (configured == null || configured.isBlank()) {
            throw new ValidationException("GITHUB_DEFAULT_BRANCH", "GITHUB_DEFAULT_BRANCH is not configured.");
        }
        return configured.trim();
    }
}
