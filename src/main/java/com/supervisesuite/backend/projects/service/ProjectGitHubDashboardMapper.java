package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ProjectGitHubDashboardMapper {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_IDLE = "idle";
    private static final String UNKNOWN_AUTHOR = "Unknown";
    private final GitHubProperties gitHubProperties;

    ProjectGitHubDashboardMapper(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
    }

    ProjectGitHubDashboardDto noRepository() {
        return new ProjectGitHubDashboardDto(
            false,
            null,
            new ProjectGitHubDashboardDto.ActivitySummary(0, null, STATUS_IDLE),
            List.of(),
            List.of()
        );
    }

    ProjectGitHubDashboardDto toDashboard(
        String repositoryUrl,
        ProjectRepositoryMetadataDto repositoryMetadata,
        List<ProjectCommitDto> commits,
        Instant now
    ) {
        List<ProjectCommitDto> safeCommits = commits == null
            ? List.of()
            : commits.stream().filter(commit -> commit != null).toList();
        List<ProjectCommitDto> newestFirstCommits = safeCommits.stream()
            .sorted(Comparator.comparing(ProjectCommitDto::getCommittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        Instant lastActivityAt = newestFirstCommits.stream()
            .map(ProjectCommitDto::getCommittedAt)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);

        ProjectGitHubDashboardDto.ActivitySummary activitySummary = new ProjectGitHubDashboardDto.ActivitySummary(
            newestFirstCommits.size(),
            lastActivityAt,
            resolveStatus(lastActivityAt, now)
        );

        return new ProjectGitHubDashboardDto(
            true,
            mapRepository(repositoryUrl, repositoryMetadata),
            activitySummary,
            mapContributors(newestFirstCommits),
            mapRecentCommits(newestFirstCommits)
        );
    }

    private ProjectGitHubDashboardDto.Repository mapRepository(
        String repositoryUrl,
        ProjectRepositoryMetadataDto metadata
    ) {
        String fallbackUrl = trimToNull(repositoryUrl);
        String name = trimToNull(metadata == null ? null : metadata.getName());
        if (name == null) {
            name = deriveRepositoryName(fallbackUrl);
        }

        String url = trimToNull(metadata == null ? null : metadata.getUrl());
        if (url == null) {
            url = fallbackUrl;
        }

        String defaultBranch = trimToNull(metadata == null ? null : metadata.getDefaultBranch());
        if (defaultBranch == null) {
            defaultBranch = defaultBranch();
        }

        return new ProjectGitHubDashboardDto.Repository(name, url, defaultBranch);
    }

    private List<ProjectGitHubDashboardDto.Contributor> mapContributors(List<ProjectCommitDto> commits) {
        Map<String, Integer> commitsByAuthor = new LinkedHashMap<>();
        for (ProjectCommitDto commit : commits) {
            String author = trimToNull(commit.getAuthor());
            String normalizedAuthor = author == null ? UNKNOWN_AUTHOR : author;
            commitsByAuthor.put(normalizedAuthor, commitsByAuthor.getOrDefault(normalizedAuthor, 0) + 1);
        }

        return commitsByAuthor.entrySet().stream()
            .sorted((left, right) -> {
                int countCompare = Integer.compare(right.getValue(), left.getValue());
                if (countCompare != 0) {
                    return countCompare;
                }
                return left.getKey().compareToIgnoreCase(right.getKey());
            })
            .limit(dashboardContributorsLimit())
            .map(entry -> new ProjectGitHubDashboardDto.Contributor(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<ProjectGitHubDashboardDto.RecentCommit> mapRecentCommits(List<ProjectCommitDto> commits) {
        return commits.stream()
            .map(commit -> new ProjectGitHubDashboardDto.RecentCommit(
                commit.getSha(),
                commit.getMessage(),
                commit.getAuthor(),
                commit.getCommittedAt()
            ))
            .toList();
    }

    private String resolveStatus(Instant lastActivityAt, Instant now) {
        if (lastActivityAt == null || now == null) {
            return STATUS_IDLE;
        }
        return lastActivityAt.isAfter(now.minus(activeWindowDuration())) ? STATUS_ACTIVE : STATUS_IDLE;
    }

    private Duration activeWindowDuration() {
        return Duration.ofHours(Math.max(1, gitHubProperties.getActivityActiveWindowHours()));
    }

    private int dashboardContributorsLimit() {
        return Math.max(1, gitHubProperties.getDashboardContributorsLimit());
    }

    private String defaultBranch() {
        String configured = gitHubProperties.getDefaultBranch();
        if (configured == null || configured.isBlank()) {
            throw new ValidationException("GITHUB_DEFAULT_BRANCH", "GITHUB_DEFAULT_BRANCH is not configured.");
        }
        return configured.trim();
    }

    private String deriveRepositoryName(String repositoryUrl) {
        String fallback = "repository";
        if (repositoryUrl == null) {
            return fallback;
        }

        try {
            URI uri = URI.create(repositoryUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return fallback;
            }

            String[] pathParts = path.split("/");
            if (pathParts.length == 0) {
                return fallback;
            }

            String candidate = pathParts[pathParts.length - 1].trim();
            if (candidate.isBlank() && pathParts.length > 1) {
                candidate = pathParts[pathParts.length - 2].trim();
            }
            if (candidate.isBlank()) {
                return fallback;
            }
            return stripGitSuffix(candidate);
        } catch (IllegalArgumentException exception) {
            String[] parts = repositoryUrl.split("/");
            if (parts.length == 0) {
                return fallback;
            }
            String candidate = parts[parts.length - 1];
            return candidate.isBlank() ? fallback : stripGitSuffix(candidate.trim());
        }
    }

    private String stripGitSuffix(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".git")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
