package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class ProjectGitHubRepositoryListingDto {

    private String projectId;
    private List<GitHubAvailableRepositoriesDto> inventory;

    public ProjectGitHubRepositoryListingDto() {
    }

    public ProjectGitHubRepositoryListingDto(String projectId, List<GitHubAvailableRepositoriesDto> inventory) {
        this.projectId = projectId;
        this.inventory = inventory;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public List<GitHubAvailableRepositoriesDto> getInventory() {
        return inventory;
    }

    public void setInventory(List<GitHubAvailableRepositoriesDto> inventory) {
        this.inventory = inventory;
    }
}
