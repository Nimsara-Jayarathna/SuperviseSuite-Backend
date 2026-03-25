package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class ProjectRepositoryLinkDto {

    private String id;
    private String sourceId;
    private String githubRepositoryId;
    private Long githubRepoId;
    private String fullName;
    private String name;
    private String customName;
    private String ownerLogin;
    private String defaultBranch;
    private String url;
    private Boolean primary;
    private Boolean enabled;
    private Instant linkedAt;
    private Instant lastSyncedAt;
    private String syncStatus;

    public ProjectRepositoryLinkDto() {
    }

    public ProjectRepositoryLinkDto(
        String id,
        String sourceId,
        String githubRepositoryId,
        Long githubRepoId,
        String fullName,
        String name,
        String customName,
        String ownerLogin,
        String defaultBranch,
        String url,
        Boolean primary,
        Boolean enabled,
        Instant linkedAt,
        Instant lastSyncedAt,
        String syncStatus
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.githubRepositoryId = githubRepositoryId;
        this.githubRepoId = githubRepoId;
        this.fullName = fullName;
        this.name = name;
        this.customName = customName;
        this.ownerLogin = ownerLogin;
        this.defaultBranch = defaultBranch;
        this.url = url;
        this.primary = primary;
        this.enabled = enabled;
        this.linkedAt = linkedAt;
        this.lastSyncedAt = lastSyncedAt;
        this.syncStatus = syncStatus;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getGithubRepositoryId() {
        return githubRepositoryId;
    }

    public void setGithubRepositoryId(String githubRepositoryId) {
        this.githubRepositoryId = githubRepositoryId;
    }

    public Long getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(Long githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}
