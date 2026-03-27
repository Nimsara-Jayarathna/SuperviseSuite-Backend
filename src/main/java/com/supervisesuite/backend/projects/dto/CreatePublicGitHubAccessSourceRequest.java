package com.supervisesuite.backend.projects.dto;

import jakarta.validation.constraints.NotBlank;

public class CreatePublicGitHubAccessSourceRequest {

    @NotBlank
    private String projectId;

    @NotBlank
    private String repositoryUrl;

    private String customName;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }
}
