package com.supervisesuite.backend.projects.dto;

import jakarta.validation.constraints.Size;

public class UpdateGitHubRepositoryDisplayNameRequest {

    @Size(max = 255, message = "Display name must not exceed 255 characters.")
    private String customName;

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }
}
