package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryDto;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubAccessMetadata;
import com.supervisesuite.backend.projects.entity.ProjectGitHubInstallationAuthorization;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkCommit;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkContributor;
import com.supervisesuite.backend.projects.integration.github.GitHubInstallationDisconnectedException;
import com.supervisesuite.backend.projects.integration.github.GitHubAppAuthService;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import com.supervisesuite.backend.projects.repository.GitHubAppInstallationRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubInstallationAuthorizationRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import com.supervisesuite.backend.projects.service.githubv2.RepositoryLinkService;
import com.supervisesuite.backend.projects.service.githubv2.GitHubIntegrationV2Constants;
import java.net.URI;
import com.supervisesuite.backend.common.error.DomainException;
import com.supervisesuite.backend.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
public class ProjectServiceImpl implements ProjectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServiceImpl.class);
    private static final int DEFAULT_PAGE = 1;
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_IDLE = "idle";

    private final GitHubCommitClient gitHubCommitClient;
    private final ProjectGitHubDashboardMapper dashboardMapper;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubAppInstallationRepository gitHubAppInstallationRepository;
    private final ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository;
    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository;
    private final ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository;
    private final RepositoryLinkService repositoryLinkService;
    private final GitHubSyncService gitHubSyncService;
    private final GitHubProperties gitHubProperties;

    public ProjectServiceImpl(
        GitHubCommitClient gitHubCommitClient,
        ProjectGitHubDashboardMapper dashboardMapper,
        GitHubAppAuthService gitHubAppAuthService,
        GitHubAppInstallationRepository gitHubAppInstallationRepository,
        ProjectGitHubInstallationAuthorizationRepository projectGitHubInstallationAuthorizationRepository,
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        ProjectRepositoryLinkCommitRepository projectRepositoryLinkCommitRepository,
        ProjectRepositoryLinkContributorRepository projectRepositoryLinkContributorRepository,
        RepositoryLinkService repositoryLinkService,
        GitHubSyncService gitHubSyncService,
        GitHubProperties gitHubProperties
    ) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.dashboardMapper = dashboardMapper;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubAppInstallationRepository = gitHubAppInstallationRepository;
        this.projectGitHubInstallationAuthorizationRepository = projectGitHubInstallationAuthorizationRepository;
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.projectRepositoryLinkCommitRepository = projectRepositoryLinkCommitRepository;
        this.projectRepositoryLinkContributorRepository = projectRepositoryLinkContributorRepository;
        this.repositoryLinkService = repositoryLinkService;
        this.gitHubSyncService = gitHubSyncService;
        this.gitHubProperties = gitHubProperties;
    }

    @Override
    public ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return dashboardMapper.noRepository();
        }
        return dashboardMapper.toDashboard(repositoryUrl, null, List.of(), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubDashboardDto getGitHubDashboard(UUID projectId, String repositoryUrl) {
        ProjectGitHubPreviewDto preview = getGitHubPreview(projectId, repositoryUrl);
        if (preview == null || !preview.isRepositoryLinked() || preview.getRepositories() == null || preview.getRepositories().isEmpty()) {
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
        ProjectGitHubAccessMetadata accessMetadata = resolveProjectGitHubAccessMetadata(projectId);
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);

        ProjectRepositoryLink link = null;
        if (normalizedUrl != null) {
            link = projectRepositoryLinkRepository.findByProjectIdAndRepositoryUrl(projectId, normalizedUrl).orElse(null);
        }

        if (link == null) {
            link = projectRepositoryLinkRepository.findTopByProjectIdOrderByCreatedAtAsc(projectId).orElse(null);
        }

        ProjectGitHubPreviewDto preview;
        if (link == null) {
            preview = new ProjectGitHubPreviewDto(
                false,
                List.of(),
                new ProjectGitHubPreviewDto.ActivitySummary(0, null, STATUS_IDLE),
                List.of(),
                List.of()
            );
            preview.setAuthorizedInstallationId(accessMetadata.authorizedInstallationId());
            preview.setAccessibleRepositoryCount(accessMetadata.accessibleRepositoryCount());
            preview.setAccessScope(accessMetadata.accessScope());
            return preview;
        }

        UUID linkId = link.getId();
        long totalCommits = projectRepositoryLinkCommitRepository.countByProjectRepositoryLinkId(linkId);
        Instant lastActivityAt = projectRepositoryLinkCommitRepository.findTopByProjectRepositoryLinkIdOrderByCommittedAtDesc(linkId)
            .map(ProjectRepositoryLinkCommit::getCommittedAt)
            .orElse(null);

        List<ProjectGitHubPreviewDto.ContributorPreviewItem> contributorsPreview = projectRepositoryLinkContributorRepository
            .findByProjectRepositoryLinkIdOrderByCommitCountDescContributorNameAsc(
                linkId,
                PageRequest.of(0, previewContributorsLimit())
            )
            .getContent()
            .stream()
            .map(contributor -> new ProjectGitHubPreviewDto.ContributorPreviewItem(
                contributor.getContributorName(),
                contributor.getCommitCount()
            ))
            .toList();

        List<ProjectGitHubPreviewDto.RecentCommitPreviewItem> recentCommitsPreview = projectRepositoryLinkCommitRepository
            .findByProjectRepositoryLinkIdOrderByCommittedAtDesc(
                linkId,
                PageRequest.of(0, previewCommitsLimit())
            )
            .getContent()
            .stream()
            .map(commit -> new ProjectGitHubPreviewDto.RecentCommitPreviewItem(
                commit.getSha(),
                commit.getMessage(),
                commit.getAuthor(),
                commit.getCommittedAt(),
                commit.getCommitType()
            ))
            .toList();

        ProjectGitHubPreviewDto.RepositoryItem repositoryItem = new ProjectGitHubPreviewDto.RepositoryItem(
            linkId.toString(),
            link.getRepositoryName(),
            link.getRepositoryUrl(),
            getHeadBranch(link),
            link.getLastSyncedAt(),
            link.getCreatedAt(),
            link.getUpdatedAt()
        );
        preview = new ProjectGitHubPreviewDto(
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
        preview.setRepositoryUrl(link.getRepositoryUrl()); // Set source of truth
        preview.setAuthorizedInstallationId(accessMetadata.authorizedInstallationId());
        preview.setAccessibleRepositoryCount(accessMetadata.accessibleRepositoryCount());
        preview.setAccessScope(accessMetadata.accessScope());
        return preview;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getGitHubActivityPage(
        UUID projectId,
        String repositoryUrl,
        int page,
        int size
    ) {
        ProjectRepositoryLink link = resolveLink(projectId, repositoryUrl);
        if (link == null) {
            int normalizedPage = normalizePage(page);
            int normalizedSize = normalizePageSize(size);
            return new ProjectGitHubPageDto<>(List.of(), normalizedPage, normalizedSize, 0, false);
        }

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "committedAt"));
        Page<ProjectRepositoryLinkCommit> data = projectRepositoryLinkCommitRepository
            .findByProjectRepositoryLinkIdOrderByCommittedAtDesc(link.getId(), pageable);

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
        ProjectRepositoryLink link = resolveLink(projectId, repositoryUrl);
        if (link == null) {
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
        Page<ProjectRepositoryLinkContributor> data = projectRepositoryLinkContributorRepository
            .findByProjectRepositoryLinkIdOrderByCommitCountDescContributorNameAsc(link.getId(), pageable);

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
        ProjectRepositoryLink link = resolveLink(projectId, repositoryUrl);
        if (link == null) {
            throw new ValidationException("repositoryUrl", "No repository linked for this project.");
        }

        try {
            gitHubSyncService.syncRepository(link.getId());
        } catch (DomainException exception) {
            if (exception instanceof GitHubInstallationDisconnectedException) {
                clearGitHubLinkage(projectId);
                LOGGER.warn(
                    "GitHub installation access removed for projectId={} repositoryUrl={}. Cleared repository links.",
                    projectId,
                    link.getRepositoryUrl()
                );
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException(nullable(exception.getMessage(), "GitHub refresh failed."), exception);
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
        // V2: This method is now simpler as it's just a precursor or handled via linkProjectToInstallationRepository
        // For compatibility, we ensure the project has an authorization record
        if (projectId == null) {
            throw new ValidationException("projectId", "Project id is required.");
        }
        projectGitHubInstallationAuthorizationRepository.findByProjectIdAndInstallationId(projectId, installationId)
            .ifPresentOrElse(
                existingAuth -> {},
                () -> {
                    ProjectGitHubInstallationAuthorization newAuth = new ProjectGitHubInstallationAuthorization();
                    newAuth.setProjectId(projectId);
                    newAuth.setInstallationId(installationId);
                    newAuth.setAuthorizedAt(Instant.now());
                    newAuth.setCreatedAt(Instant.now());
                    projectGitHubInstallationAuthorizationRepository.save(newAuth);
                }
            );
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
        requireProjectInstallationAuthorization(projectId, installationId);

        GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context =
            repositoryLinkService.fetchInstallationRepositories(installationId, normalizedPage, normalizedSize);

        List<GitHubInstallationRepositoryDto> items = context.repositories().stream()
            .map(repository -> new GitHubInstallationRepositoryDto(
                repository.repositoryId(),
                repository.repositoryName(),
                repository.fullName(),
                repository.htmlUrl(),
                repository.ownerLogin(),
                repository.defaultBranch()
            ))
            .toList();

        return new GitHubInstallationRepositoryPageDto(
            items,
            normalizedPage,
            normalizedSize,
            items.size(),
            context.totalCount(),
            context.totalCount() != null && (long) normalizedPage * normalizedSize < context.totalCount(),
            normalizedPage > 1,
            (context.totalCount() != null && (long) normalizedPage * normalizedSize < context.totalCount()) ? normalizedPage + 1 : null
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
        ProjectRepositoryLink link = repositoryLinkService.linkRepository(projectId, installationId, repositoryId, supervisorUserId);
        
        if (link != null) {
            try {
                gitHubSyncService.syncRepository(link.getId());
            } catch (Exception e) {
                LOGGER.warn("Initial sync failed for link {}: {}", link.getId(), e.getMessage());
            }
        }

        return new ProjectGitHubRepositoryLinkDto(
            projectId,
            installationId,
            repositoryId,
            link != null ? link.getRepositoryName() : null,
            link != null ? link.getRepositoryName() : null, // full name simplified
            link != null ? link.getRepositoryUrl() : null,
            null, // ownerLogin if needed
            link != null ? link.getDefaultBranch() : null,
            link != null ? link.getLastSyncedAt() : null
        );
    }

    @Override
    @Transactional
    public void switchToManualRepository(UUID projectId, String repositoryUrl) {
        // In V2, "Manual" is just another RepositoryLink with a specific source type if we had one, 
        // but here we just ensure a link exists for this URL and is primary.
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        repositoryLinkService.linkRepositoryByUrl(projectId, normalizedUrl, null);
    }

    @Override
    @Transactional
    public void clearGitHubLinkage(UUID projectId) {
        List<ProjectRepositoryLink> links = projectRepositoryLinkRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        for (ProjectRepositoryLink link : links) {
            repositoryLinkService.disconnectRepository(link.getId());
        }
        projectGitHubInstallationAuthorizationRepository.deleteByProjectId(projectId);
    }

    private ProjectRepositoryLink resolveLink(UUID projectId, String repositoryUrl) {
        String normalizedUrl = normalizeRepositoryUrl(repositoryUrl);
        if (normalizedUrl != null) {
            return projectRepositoryLinkRepository.findByProjectIdAndRepositoryUrl(projectId, normalizedUrl).orElse(null);
        }
        return projectRepositoryLinkRepository.findByProjectIdAndIsPrimaryTrue(projectId)
            .orElseGet(() -> projectRepositoryLinkRepository.findTopByProjectIdOrderByCreatedAtAsc(projectId).orElse(null));
    }

    private String resolveStatus(Instant lastActivityAt, Instant now) {
        if (lastActivityAt == null || now == null) {
            return STATUS_IDLE;
        }
        return lastActivityAt.isAfter(now.minus(activeWindowDuration())) ? STATUS_ACTIVE : STATUS_IDLE;
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
        return page < 1 ? 1 : page;
    }

    private int normalizeInstallationRepositoriesPageSize(Integer size) {
        if (size < 1) {
            throw new ValidationException("size", "Size must be greater than zero.");
        }
        return Math.min(size, installationRepositoriesMaxPageSize());
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

    private String getHeadBranch(ProjectRepositoryLink link) {
        if (link == null) {
            return GitHubIntegrationV2Constants.DEFAULT_BRANCH;
        }
        return link.getDefaultBranch() != null ? link.getDefaultBranch() : GitHubIntegrationV2Constants.DEFAULT_BRANCH;
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

    private void validateGitHubInstallationAccess(UUID projectId, Long installationId) {
        gitHubAppInstallationRepository.findByInstallationId(installationId)
            .ifPresent(installation -> {
                boolean authorized = projectGitHubInstallationAuthorizationRepository
                    .existsByProjectIdAndInstallationId(projectId, installation.getInstallationId());
                if (!authorized) {
                    throw new DomainException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Project is not authorized to access this GitHub installation.");
                }
            });
    }

    private void requireUsableInstallation(Long installationId) {
        if (installationId == null || installationId < 1) {
            throw new ValidationException("installationId", "GitHub installation id is required.");
        }
        gitHubAppInstallationRepository.findByInstallationId(installationId)
            .orElseThrow(() -> new ValidationException("installationId", "GitHub installation not found."));
    }

    private void requireProjectInstallationAuthorization(UUID projectId, Long installationId) {
        validateGitHubInstallationAccess(projectId, installationId);
    }

    private ProjectGitHubAccessMetadata resolveProjectGitHubAccessMetadata(UUID projectId) {
        return repositoryLinkService.resolveLink(projectId);
    }

    private int previewContributorsLimit() {
        return gitHubProperties.getPreviewContributorsLimit();
    }

    private int previewCommitsLimit() {
        return gitHubProperties.getPreviewCommitsLimit();
    }

    private int activityCommitsMaxPageSize() {
        return gitHubProperties.getMaxPageSize();
    }

    private int installationRepositoriesMaxPageSize() {
        return gitHubProperties.getInstallationRepositories().getMaxPageSize();
    }

    private Duration activeWindowDuration() {
        return Duration.ofHours(gitHubProperties.getActivityActiveWindowHours());
    }

    private String normalizeRepositoryUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.toLowerCase().endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private String defaultBranch() {
        String configured = gitHubProperties.getDefaultBranch();
        if (configured == null || configured.isBlank()) {
            return GitHubIntegrationV2Constants.DEFAULT_BRANCH;
        }
        return configured.trim();
    }
}
