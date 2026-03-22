package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.entity.ProjectRepository;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryCommit;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryContributor;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
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
    private static final Duration ACTIVE_WINDOW = Duration.ofHours(48);
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_IDLE = "idle";
    private static final String PROVIDER_GITHUB = "github";
    private static final int PREVIEW_COMMITS_LIMIT = 6;

    private final GitHubCommitClient gitHubCommitClient;
    private final ProjectGitHubDashboardMapper dashboardMapper;
    private final ProjectRepositoryCacheRepository projectRepositoryCacheRepository;
    private final ProjectRepositoryCommitRepository projectRepositoryCommitRepository;
    private final ProjectRepositoryContributorRepository projectRepositoryContributorRepository;
    private final GitHubAppInstallationRepository gitHubAppInstallationRepository;
    private final GitHubProperties gitHubProperties;

    ProjectServiceImpl(
        GitHubCommitClient gitHubCommitClient,
        ProjectGitHubDashboardMapper dashboardMapper,
        ProjectRepositoryCacheRepository projectRepositoryCacheRepository,
        ProjectRepositoryCommitRepository projectRepositoryCommitRepository,
        ProjectRepositoryContributorRepository projectRepositoryContributorRepository,
        GitHubAppInstallationRepository gitHubAppInstallationRepository,
        GitHubProperties gitHubProperties
    ) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.dashboardMapper = dashboardMapper;
        this.projectRepositoryCacheRepository = projectRepositoryCacheRepository;
        this.projectRepositoryCommitRepository = projectRepositoryCommitRepository;
        this.projectRepositoryContributorRepository = projectRepositoryContributorRepository;
        this.gitHubAppInstallationRepository = gitHubAppInstallationRepository;
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
                .findTop4ByRepositoryIdOrderByCommitCountDescContributorNameAsc(repository.getId())
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
                .limit(PREVIEW_COMMITS_LIMIT)
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
            nullable(repository.getDefaultBranch(), "main"),
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
    @Transactional
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
            repository.setDefaultBranch(nullable(metadata.getDefaultBranch(), "main"));
            repository.setLastSyncedAt(now);
            repository.setSyncStatus("SUCCESS");
            repository.setLastSyncError(null);
            repository.setUpdatedAt(now);
            repository = projectRepositoryCacheRepository.save(repository);

            syncCommits(repository.getId(), commits, now);
            syncContributors(repository.getId(), commits, now);
        } catch (RuntimeException exception) {
            String failureMessage = nullable(exception.getMessage(), "GitHub refresh failed.");
            repository.setLastSyncedAt(now);
            repository.setSyncStatus("FAILED");
            repository.setLastSyncError(failureMessage);
            repository.setUpdatedAt(now);
            projectRepositoryCacheRepository.save(repository);
            LOGGER.warn(
                "GitHub refresh failed for projectId={} repositoryUrl={}: {}",
                projectId,
                targetRepositoryUrl,
                failureMessage
            );
            throw new ServiceUnavailableException(failureMessage, exception);
        }
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
        repository.setUpdatedAt(now);
        projectRepositoryCacheRepository.save(repository);
    }

    @Override
    @Transactional
    public void onRepositoryUrlUpdated(UUID projectId, String repositoryUrl) {
        Instant now = Instant.now();
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        List<ProjectRepository> repositories = projectRepositoryCacheRepository.findByProjectIdOrderByCreatedAtAsc(projectId);

        if (normalizedUrl == null) {
            for (ProjectRepository repository : repositories) {
                if (Boolean.TRUE.equals(repository.getIsPrimary())) {
                    repository.setIsPrimary(false);
                    repository.setUpdatedAt(now);
                }
            }
            if (!repositories.isEmpty()) {
                projectRepositoryCacheRepository.saveAll(repositories);
            }
            return;
        }

        ProjectRepository target = projectRepositoryCacheRepository
            .findByProjectIdAndProviderAndRepositoryUrl(projectId, PROVIDER_GITHUB, normalizedUrl)
            .orElseGet(() -> {
                ProjectRepository created = new ProjectRepository();
                created.setProjectId(projectId);
                created.setProvider(PROVIDER_GITHUB);
                created.setRepositoryUrl(normalizedUrl);
                created.setRepositoryName(deriveRepositoryName(normalizedUrl));
                created.setDefaultBranch("main");
                created.setCreatedAt(now);
                return created;
            });

        for (ProjectRepository repository : repositories) {
            if (repository.getId() != null && !repository.getId().equals(target.getId())) {
                repository.setIsPrimary(false);
                repository.setUpdatedAt(now);
            }
        }

        target.setProjectId(projectId);
        target.setProvider(PROVIDER_GITHUB);
        target.setRepositoryUrl(normalizedUrl);
        target.setIsPrimary(true);
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(now);
        }
        target.setUpdatedAt(now);

        projectRepositoryCacheRepository.save(target);
        if (!repositories.isEmpty()) {
            projectRepositoryCacheRepository.saveAll(
                repositories.stream()
                    .filter(repository -> repository.getId() != null && !repository.getId().equals(target.getId()))
                    .toList()
            );
        }
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
        target.setSyncStatus(null);
        target.setLastSyncError(null);
        target.setUpdatedAt(now);
        projectRepositoryCacheRepository.save(target);

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
                created.setDefaultBranch("main");
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
        repository.setDefaultBranch("main");
        repository.setIsPrimary(true);
        return repository;
    }

    private String resolveStatus(Instant lastActivityAt, Instant now) {
        if (lastActivityAt == null || now == null) {
            return STATUS_IDLE;
        }
        return lastActivityAt.isAfter(now.minus(ACTIVE_WINDOW)) ? STATUS_ACTIVE : STATUS_IDLE;
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

    private String normalizeRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        return repositoryUrl.trim();
    }

    private int normalizePageSize(int size) {
        int configuredDefault = gitHubProperties.getDefaultPageSize() > 0
            ? gitHubProperties.getDefaultPageSize()
            : 10;
        int configuredMax = gitHubProperties.getMaxPageSize() > 0
            ? gitHubProperties.getMaxPageSize()
            : 100;

        if (size < 1) {
            return configuredDefault;
        }
        return Math.min(size, configuredMax);
    }
}
