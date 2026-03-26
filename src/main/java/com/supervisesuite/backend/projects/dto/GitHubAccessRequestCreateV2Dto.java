package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class GitHubAccessRequestCreateV2Dto {

    private String projectId;
    private String requestUrl;
    private Instant expiresAt;

    public GitHubAccessRequestCreateV2Dto() {
    }

    public GitHubAccessRequestCreateV2Dto(String projectId, String requestUrl, Instant expiresAt) {
        this.projectId = projectId;
        this.requestUrl = requestUrl;
        this.expiresAt = expiresAt;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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
