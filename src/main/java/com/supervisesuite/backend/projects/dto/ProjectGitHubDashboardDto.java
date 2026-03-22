package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.List;

public class ProjectGitHubDashboardDto {
    private boolean repositoryLinked;
    private Repository repository;
    private ActivitySummary activitySummary;
    private List<Contributor> contributors;
    private List<RecentCommit> recentCommits;

    public ProjectGitHubDashboardDto() {
    }

    public ProjectGitHubDashboardDto(
        boolean repositoryLinked,
        Repository repository,
        ActivitySummary activitySummary,
        List<Contributor> contributors,
        List<RecentCommit> recentCommits
    ) {
        this.repositoryLinked = repositoryLinked;
        this.repository = repository;
        this.activitySummary = activitySummary;
        this.contributors = contributors;
        this.recentCommits = recentCommits;
    }

    public boolean isRepositoryLinked() {
        return repositoryLinked;
    }

    public void setRepositoryLinked(boolean repositoryLinked) {
        this.repositoryLinked = repositoryLinked;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public ActivitySummary getActivitySummary() {
        return activitySummary;
    }

    public void setActivitySummary(ActivitySummary activitySummary) {
        this.activitySummary = activitySummary;
    }

    public List<Contributor> getContributors() {
        return contributors;
    }

    public void setContributors(List<Contributor> contributors) {
        this.contributors = contributors;
    }

    public List<RecentCommit> getRecentCommits() {
        return recentCommits;
    }

    public void setRecentCommits(List<RecentCommit> recentCommits) {
        this.recentCommits = recentCommits;
    }

    public static class Repository {
        private String name;
        private String url;
        private String defaultBranch;

        public Repository() {
        }

        public Repository(String name, String url, String defaultBranch) {
            this.name = name;
            this.url = url;
            this.defaultBranch = defaultBranch;
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

    public static class Contributor {
        private String name;
        private int commitCount;

        public Contributor() {
        }

        public Contributor(String name, int commitCount) {
            this.name = name;
            this.commitCount = commitCount;
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
    }

    public static class RecentCommit {
        private String sha;
        private String message;
        private String author;
        private Instant committedAt;

        public RecentCommit() {
        }

        public RecentCommit(String sha, String message, String author, Instant committedAt) {
            this.sha = sha;
            this.message = message;
            this.author = author;
            this.committedAt = committedAt;
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

        public Instant getCommittedAt() {
            return committedAt;
        }

        public void setCommittedAt(Instant committedAt) {
            this.committedAt = committedAt;
        }
    }
}
