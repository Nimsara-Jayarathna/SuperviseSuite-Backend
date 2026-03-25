package com.supervisesuite.backend.projects.dto;

import java.time.Instant;

public class GitHubInstallStartDto {

    private String projectId;
    private String githubAuthorizeUrl;
    private String flowType;
    private Instant expiresAt;

    public GitHubInstallStartDto() {
    }

    public GitHubInstallStartDto(String projectId, String githubAuthorizeUrl, String flowType, Instant expiresAt) {
        this.projectId = projectId;
        this.githubAuthorizeUrl = githubAuthorizeUrl;
        this.flowType = flowType;
        this.expiresAt = expiresAt;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getGithubAuthorizeUrl() {
        return githubAuthorizeUrl;
    }

    public void setGithubAuthorizeUrl(String githubAuthorizeUrl) {
        this.githubAuthorizeUrl = githubAuthorizeUrl;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
