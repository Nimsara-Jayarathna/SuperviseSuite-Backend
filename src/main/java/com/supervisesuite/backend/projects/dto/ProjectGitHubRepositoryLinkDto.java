package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.UUID;

public class ProjectGitHubRepositoryLinkDto {

    private UUID projectId;
    private Long installationId;
    private Long repositoryId;
    private String name;
    private String fullName;
    private String url;
    private String ownerLogin;
    private String defaultBranch;
    private Instant lastSyncedAt;

    public ProjectGitHubRepositoryLinkDto() {
    }

    public ProjectGitHubRepositoryLinkDto(
        UUID projectId,
        Long installationId,
        Long repositoryId,
        String name,
        String fullName,
        String url,
        String ownerLogin,
        String defaultBranch,
        Instant lastSyncedAt
    ) {
        this.projectId = projectId;
        this.installationId = installationId;
        this.repositoryId = repositoryId;
        this.name = name;
        this.fullName = fullName;
        this.url = url;
        this.ownerLogin = ownerLogin;
        this.defaultBranch = defaultBranch;
        this.lastSyncedAt = lastSyncedAt;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
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
}
