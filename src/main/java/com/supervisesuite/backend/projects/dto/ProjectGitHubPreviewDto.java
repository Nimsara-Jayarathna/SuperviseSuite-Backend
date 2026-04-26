package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.List;

public class ProjectGitHubPreviewDto {
    private boolean repositoryLinked;
    private Long authorizedInstallationId;
    private Integer accessibleRepositoryCount;
    private String accessScope;
    private List<RepositoryItem> repositories;
    private ActivitySummary activitySummary;
    private List<ContributorPreviewItem> contributorsPreview;
    private List<RecentCommitPreviewItem> recentCommitsPreview;
    private String repositoryUrl;
    private boolean hasUnacknowledgedAccess;

    public ProjectGitHubPreviewDto() {
    }

    public ProjectGitHubPreviewDto(
        boolean repositoryLinked,
        List<RepositoryItem> repositories,
        ActivitySummary activitySummary,
        List<ContributorPreviewItem> contributorsPreview,
        List<RecentCommitPreviewItem> recentCommitsPreview
    ) {
        this.repositoryLinked = repositoryLinked;
        this.repositories = repositories;
        this.activitySummary = activitySummary;
        this.contributorsPreview = contributorsPreview;
        this.recentCommitsPreview = recentCommitsPreview;
    }

    public boolean isRepositoryLinked() {
        return repositoryLinked;
    }

    public void setRepositoryLinked(boolean repositoryLinked) {
        this.repositoryLinked = repositoryLinked;
    }

    public Long getAuthorizedInstallationId() {
        return authorizedInstallationId;
    }

    public void setAuthorizedInstallationId(Long authorizedInstallationId) {
        this.authorizedInstallationId = authorizedInstallationId;
    }

    public Integer getAccessibleRepositoryCount() {
        return accessibleRepositoryCount;
    }

    public void setAccessibleRepositoryCount(Integer accessibleRepositoryCount) {
        this.accessibleRepositoryCount = accessibleRepositoryCount;
    }

    public String getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(String accessScope) {
        this.accessScope = accessScope;
    }

    public List<RepositoryItem> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<RepositoryItem> repositories) {
        this.repositories = repositories;
    }

    public ActivitySummary getActivitySummary() {
        return activitySummary;
    }

    public void setActivitySummary(ActivitySummary activitySummary) {
        this.activitySummary = activitySummary;
    }

    public List<ContributorPreviewItem> getContributorsPreview() {
        return contributorsPreview;
    }

    public void setContributorsPreview(List<ContributorPreviewItem> contributorsPreview) {
        this.contributorsPreview = contributorsPreview;
    }

    public List<RecentCommitPreviewItem> getRecentCommitsPreview() {
        return recentCommitsPreview;
    }

    public void setRecentCommitsPreview(List<RecentCommitPreviewItem> recentCommitsPreview) {
        this.recentCommitsPreview = recentCommitsPreview;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public boolean isHasUnacknowledgedAccess() {
        return hasUnacknowledgedAccess;
    }

    public void setHasUnacknowledgedAccess(boolean hasUnacknowledgedAccess) {
        this.hasUnacknowledgedAccess = hasUnacknowledgedAccess;
    }

    public static class RepositoryItem {
        private String id;
        private String name;
        private String url;
        private String defaultBranch;
        private Instant lastSyncedAt;
        private Instant createdAt;
        private Instant updatedAt;

        public RepositoryItem() {
        }

        public RepositoryItem(String id, String name, String url, String defaultBranch, Instant lastSyncedAt, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.defaultBranch = defaultBranch;
            this.lastSyncedAt = lastSyncedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDefaultBranch() {
            return defaultBranch;
        }

        public void setDefaultBranch(String defaultBranch) {
            this.defaultBranch = defaultBranch;
        }

        public Instant getLastSyncedAt() {
            return lastSyncedAt;
        }

        public void setLastSyncedAt(Instant lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class ActivitySummary {
        private int totalCommits;
        private Instant lastActivityAt;
        private String status;

        public ActivitySummary() {
        }

        public ActivitySummary(int totalCommits, Instant lastActivityAt, String status) {
            this.totalCommits = totalCommits;
            this.lastActivityAt = lastActivityAt;
            this.status = status;
        }

        public int getTotalCommits() {
            return totalCommits;
        }

        public void setTotalCommits(int totalCommits) {
            this.totalCommits = totalCommits;
        }

        public Instant getLastActivityAt() {
            return lastActivityAt;
        }

        public void setLastActivityAt(Instant lastActivityAt) {
            this.lastActivityAt = lastActivityAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class ContributorPreviewItem {
        private String name;
        private int commitCount;
        private String githubUsername;
        private String avatarUrl;

        public ContributorPreviewItem() {
        }

        public ContributorPreviewItem(String name, int commitCount) {
            this(name, commitCount, null, null);
        }

        public ContributorPreviewItem(String name, int commitCount, String githubUsername, String avatarUrl) {
            this.name = name;
            this.commitCount = commitCount;
            this.githubUsername = githubUsername;
            this.avatarUrl = avatarUrl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCommitCount() {
            return commitCount;
        }

        public void setCommitCount(int commitCount) {
            this.commitCount = commitCount;
        }

        public String getGithubUsername() {
            return githubUsername;
        }

        public void setGithubUsername(String githubUsername) {
            this.githubUsername = githubUsername;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    public static class RecentCommitPreviewItem {
        private String sha;
        private String message;
        private String author;
        private String githubUsername;
        private String avatarUrl;
        private Instant committedAt;
        private String type;

        public RecentCommitPreviewItem() {
        }

        public RecentCommitPreviewItem(
            String sha,
            String message,
            String author,
            Instant committedAt,
            String type
        ) {
            this(sha, message, author, null, null, committedAt, type);
        }

        public RecentCommitPreviewItem(
            String sha,
            String message,
            String author,
            String githubUsername,
            String avatarUrl,
            Instant committedAt,
            String type
        ) {
            this.sha = sha;
            this.message = message;
            this.author = author;
            this.githubUsername = githubUsername;
            this.avatarUrl = avatarUrl;
            this.committedAt = committedAt;
            this.type = type;
        }

        public String getSha() {
            return sha;
        }

        public void setSha(String sha) {
            this.sha = sha;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getGithubUsername() {
            return githubUsername;
        }

        public void setGithubUsername(String githubUsername) {
            this.githubUsername = githubUsername;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public Instant getCommittedAt() {
            return committedAt;
        }

        public void setCommittedAt(Instant committedAt) {
            this.committedAt = committedAt;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
