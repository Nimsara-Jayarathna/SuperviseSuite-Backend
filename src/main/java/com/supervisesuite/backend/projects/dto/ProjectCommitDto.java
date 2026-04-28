package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class ProjectCommitDto {
    private String sha;
    private String message;
    private String authorName;
    private String githubUsername;
    private String githubAvatarUrl;
    private Instant committedAt;

    public ProjectCommitDto() {
    }

    public ProjectCommitDto(String sha, String message, String author, Instant committedAt) {
        this(sha, message, author, null, null, committedAt);
    }

    public ProjectCommitDto(
        String sha,
        String message,
        String authorName,
        String githubUsername,
        String githubAvatarUrl,
        Instant committedAt
    ) {
        this.sha = sha;
        this.message = message;
        this.authorName = authorName;
        this.githubUsername = githubUsername;
        this.githubAvatarUrl = githubAvatarUrl;
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
        return authorName;
    }

    public void setAuthor(String author) {
        this.authorName = author;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getGithubAvatarUrl() {
        return githubAvatarUrl;
    }

    public void setGithubAvatarUrl(String githubAvatarUrl) {
        this.githubAvatarUrl = githubAvatarUrl;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(Instant committedAt) {
        this.committedAt = committedAt;
    }
}
