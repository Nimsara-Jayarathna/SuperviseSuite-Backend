package com.supervisesuite.backend.projects.dto;

import java.util.UUID;

public class GitHubAccessUpdatedAcknowledgeDto {

    private UUID projectId;

    public GitHubAccessUpdatedAcknowledgeDto() {
    }

    public GitHubAccessUpdatedAcknowledgeDto(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }
}
