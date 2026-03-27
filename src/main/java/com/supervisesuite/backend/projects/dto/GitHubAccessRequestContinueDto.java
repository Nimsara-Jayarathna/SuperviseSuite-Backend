package com.supervisesuite.backend.projects.dto;

import java.util.UUID;

public class GitHubAccessRequestContinueDto {

    private UUID projectId;
    private String githubAuthorizeUrl;

    public GitHubAccessRequestContinueDto() {
    }

    public GitHubAccessRequestContinueDto(UUID projectId, String githubAuthorizeUrl) {
        this.projectId = projectId;
        this.githubAuthorizeUrl = githubAuthorizeUrl;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getGithubAuthorizeUrl() {
        return githubAuthorizeUrl;
    }

    public void setGithubAuthorizeUrl(String githubAuthorizeUrl) {
        this.githubAuthorizeUrl = githubAuthorizeUrl;
    }
}
