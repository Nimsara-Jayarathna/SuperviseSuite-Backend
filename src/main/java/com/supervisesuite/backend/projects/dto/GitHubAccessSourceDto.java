package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class GitHubAccessSourceDto {

    private String id;
    private String projectId;
    private Long installationId;
    private String ownerLogin;
    private String ownerType;
    private String accessType;
    private Boolean active;
    private Instant createdAt;

    public GitHubAccessSourceDto() {
    }

    public GitHubAccessSourceDto(
        String id,
        String projectId,
        Long installationId,
        String ownerLogin,
        String ownerType,
        String accessType,
        Boolean active,
        Instant createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.installationId = installationId;
        this.ownerLogin = ownerLogin;
        this.ownerType = ownerType;
        this.accessType = accessType;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public void setOwnerLogin(String ownerLogin) {
        this.ownerLogin = ownerLogin;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
