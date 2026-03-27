package com.supervisesuite.backend.projects.dto;

import java.time.Instant;
import java.util.UUID;

public class GitHubAccessRequestCreateDto {

    private UUID projectId;
    private String requestToken;
    private String requestUrl;
    private Instant expiresAt;

    public GitHubAccessRequestCreateDto() {
    }

    public GitHubAccessRequestCreateDto(
        UUID projectId,
        String requestToken,
        String requestUrl,
        Instant expiresAt
    ) {
        this.projectId = projectId;
        this.requestToken = requestToken;
        this.requestUrl = requestUrl;
        this.expiresAt = expiresAt;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getRequestToken() {
        return requestToken;
    }

    public void setRequestToken(String requestToken) {
        this.requestToken = requestToken;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
