package com.supervisesuite.backend.projects.service.githubv2;

import com.supervisesuite.backend.common.error.ConflictException;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.entity.GitHubAccessSource;
import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkCommit;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkContributor;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubSyncService {

    private final ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    private final GitHubAccessSourceRepository accessSourceRepository;
    private final GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;
    private final ProjectRepositoryLinkCommitRepository commitRepository;
    private final ProjectRepositoryLinkContributorRepository contributorRepository;
    private final GitHubClient gitHubClient;

    public GitHubSyncService(
        ProjectRepositoryLinkRepository projectRepositoryLinkRepository,
        GitHubAccessSourceRepository accessSourceRepository,
        GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository,
        ProjectRepositoryLinkCommitRepository commitRepository,
        ProjectRepositoryLinkContributorRepository contributorRepository,
        GitHubClient gitHubClient
    ) {
        this.projectRepositoryLinkRepository = projectRepositoryLinkRepository;
        this.accessSourceRepository = accessSourceRepository;
        this.gitHubRepositoryEntityRepository = gitHubRepositoryEntityRepository;
        this.commitRepository = commitRepository;
        this.contributorRepository = contributorRepository;
        this.gitHubClient = gitHubClient;
    }

    @Transactional
    public void syncRepository(UUID linkId) {
        refreshRepository(linkId);
    }

    @Transactional
    public void refreshRepository(UUID linkId) {
        ProjectRepositoryLink link = projectRepositoryLinkRepository
            .findById(linkId)
            .orElseThrow(() -> new ValidationException("repositoryId", "Linked repository was not found."));
        if (!Boolean.TRUE.equals(link.getIsEnabled())) {
            throw new ConflictException("Disabled repositories cannot be refreshed.");
        }

        GitHubRepositoryEntity repositoryEntity = gitHubRepositoryEntityRepository
            .findById(link.getGithubRepositoryId())
            .orElseThrow(() -> new ValidationException("githubRepositoryId", "GitHub repository was not found."));

        GitHubAccessSource accessSource = accessSourceRepository
            .findById(repositoryEntity.getAccessSourceId())
            .orElseThrow(() -> new ValidationException("sourceId", "GitHub access source was not found."));

        Instant now = Instant.now();
        try {
            String repositoryUrl = resolveRepositoryUrl(repositoryEntity);
            ProjectRepositoryMetadataDto metadata = gitHubClient.fetchRepositoryMetadata(
                repositoryUrl,
                accessSource.getInstallationId()
            );
            List<ProjectCommitDto> commits = gitHubClient.fetchRecentCommits(repositoryUrl, accessSource.getInstallationId());

            applyRepositoryMetadata(repositoryEntity, metadata);
            gitHubRepositoryEntityRepository.save(repositoryEntity);

            upsertCommits(link.getId(), commits, now);
            upsertContributors(link.getId(), now);

            link.setLastSyncedAt(now);
            link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_SUCCESS);
            link.setSyncError(null);
            link.setUpdatedAt(now);
            projectRepositoryLinkRepository.save(link);
        } catch (RuntimeException exception) {
            link.setLastSyncedAt(now);
            link.setSyncStatus(GitHubIntegrationV2Constants.SYNC_STATUS_FAILED);
            link.setSyncError(nullable(exception.getMessage(), "GitHub repository sync failed."));
            link.setUpdatedAt(now);
            projectRepositoryLinkRepository.save(link);
            throw new ServiceUnavailableException(nullable(exception.getMessage(), "GitHub repository sync failed."), exception);
        }
    }

    private void applyRepositoryMetadata(GitHubRepositoryEntity repositoryEntity, ProjectRepositoryMetadataDto metadata) {
        if (metadata == null) {
            return;
        }

        if (metadata.getExternalRepositoryId() != null) {
            repositoryEntity.setGithubRepoId(metadata.getExternalRepositoryId());
        }

        if (hasText(metadata.getName())) {
            repositoryEntity.setName(metadata.getName().trim());
        }

        if (hasText(metadata.getOwnerLogin())) {
            String ownerLogin = metadata.getOwnerLogin().trim();
            repositoryEntity.setOwnerLogin(ownerLogin);
            repositoryEntity.setFullName(ownerLogin + "/" + nullable(repositoryEntity.getName(), "repository"));
        }

        if (hasText(metadata.getUrl())) {
            repositoryEntity.setHtmlUrl(metadata.getUrl().trim());
        }

        if (hasText(metadata.getDefaultBranch())) {
            repositoryEntity.setDefaultBranch(metadata.getDefaultBranch().trim());
        }
    }

    private void upsertCommits(UUID linkId, List<ProjectCommitDto> commits, Instant now) {
        for (ProjectCommitDto commit : commits == null ? List.<ProjectCommitDto>of() : commits) {
            if (commit == null || !hasText(commit.getSha())) {
                continue;
            }

            ProjectRepositoryLinkCommit entity = commitRepository
                .findByProjectRepositoryLinkIdAndSha(linkId, commit.getSha())
                .orElseGet(() -> {
                    ProjectRepositoryLinkCommit created = new ProjectRepositoryLinkCommit();
                    created.setProjectRepositoryLinkId(linkId);
                    created.setSha(commit.getSha());
                    created.setCreatedAt(now);
                    return created;
                });

            entity.setMessage(nullable(commit.getMessage(), ""));
            entity.setAuthor(nullable(commit.getAuthor(), "Unknown"));
            entity.setCommittedAt(commit.getCommittedAt());
            entity.setCommitType(resolveCommitType(commit.getMessage()));
            entity.setUpdatedAt(now);
            commitRepository.save(entity);
        }
    }

    private void upsertContributors(UUID linkId, Instant now) {
        List<ProjectRepositoryLinkCommit> commits = commitRepository.findByProjectRepositoryLinkId(linkId);

        Map<String, Integer> countByContributor = new HashMap<>();
        Map<String, Instant> lastByContributor = new HashMap<>();

        for (ProjectRepositoryLinkCommit commit : commits) {
            String contributor = nullable(commit.getAuthor(), "Unknown");
            countByContributor.put(contributor, countByContributor.getOrDefault(contributor, 0) + 1);

            if (commit.getCommittedAt() != null) {
                Instant existing = lastByContributor.get(contributor);
                if (existing == null || commit.getCommittedAt().isAfter(existing)) {
                    lastByContributor.put(contributor, commit.getCommittedAt());
                }
            }
        }

        if (countByContributor.isEmpty()) {
            contributorRepository.deleteByProjectRepositoryLinkId(linkId);
            return;
        }

        for (Map.Entry<String, Integer> entry : countByContributor.entrySet()) {
            String contributorName = entry.getKey();
            ProjectRepositoryLinkContributor entity = contributorRepository
                .findByProjectRepositoryLinkIdAndContributorName(linkId, contributorName)
                .orElseGet(() -> {
                    ProjectRepositoryLinkContributor created = new ProjectRepositoryLinkContributor();
                    created.setProjectRepositoryLinkId(linkId);
                    created.setContributorName(contributorName);
                    return created;
                });

            entity.setCommitCount(entry.getValue());
            entity.setLastContributionAt(lastByContributor.get(contributorName));
            entity.setUpdatedAt(now);
            contributorRepository.save(entity);
        }

        contributorRepository.deleteStaleContributors(linkId, countByContributor.keySet());
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
        if (normalized.startsWith("docs")) {
            return "docs";
        }
        if (normalized.startsWith("refactor")) {
            return "refactor";
        }
        return null;
    }

    private String resolveRepositoryUrl(GitHubRepositoryEntity repositoryEntity) {
        if (hasText(repositoryEntity.getHtmlUrl())) {
            return repositoryEntity.getHtmlUrl().trim();
        }
        if (!hasText(repositoryEntity.getFullName())) {
            throw new ValidationException("repository", "Repository URL is missing.");
        }
        return "https://github.com/" + repositoryEntity.getFullName().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullable(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}
