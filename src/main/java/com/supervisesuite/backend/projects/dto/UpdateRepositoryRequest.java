package com.supervisesuite.backend.projects.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateRepositoryRequest {

    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    @Pattern(
        regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$",
        message = "Repository URL must be a valid GitHub repository URL (e.g., https://github.com/owner/repo)"
    )
    private String repositoryUrl;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
