package com.supervisesuite.backend.projects.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateGitHubAccessRequestRequest {

    @NotBlank
    private String projectId;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}
