package com.supervisesuite.backend.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class LinkProjectGitHubRepositoryRequest {

    @NotNull(message = "installationId is required.")
    @Positive(message = "installationId must be greater than zero.")
    private Long installationId;

    @NotNull(message = "repositoryId is required.")
    @Positive(message = "repositoryId must be greater than zero.")
    private Long repositoryId;

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }
}
