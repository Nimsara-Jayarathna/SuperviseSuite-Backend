package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.UUID;

public class GitHubAccessRequestValidationDto {

    private UUID projectId;
    private String projectTitle;
    private String status;
    private Instant expiresAt;

    public GitHubAccessRequestValidationDto() {
    }

    public GitHubAccessRequestValidationDto(
        UUID projectId,
        String projectTitle,
        String status,
        Instant expiresAt
    ) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
