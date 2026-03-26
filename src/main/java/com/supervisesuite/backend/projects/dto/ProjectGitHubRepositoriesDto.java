package com.supervisesuite.backend.projects.dto;

import java.util.List;

public class ProjectGitHubRepositoriesDto {

    private String projectId;
    private Integer maxLinkedRepositories;
    private Integer maxEnabledRepositories;
    private List<GitHubAccessSourceDto> accessSources;
    private List<ProjectRepositoryLinkDto> repositories;

    public ProjectGitHubRepositoriesDto() {
    }

    public ProjectGitHubRepositoriesDto(
        String projectId,
        Integer maxLinkedRepositories,
        Integer maxEnabledRepositories,
        List<GitHubAccessSourceDto> accessSources,
        List<ProjectRepositoryLinkDto> repositories
    ) {
        this.projectId = projectId;
        this.maxLinkedRepositories = maxLinkedRepositories;
        this.maxEnabledRepositories = maxEnabledRepositories;
        this.accessSources = accessSources;
        this.repositories = repositories;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Integer getMaxLinkedRepositories() {
        return maxLinkedRepositories;
    }

    public void setMaxLinkedRepositories(Integer maxLinkedRepositories) {
        this.maxLinkedRepositories = maxLinkedRepositories;
    }

    public Integer getMaxEnabledRepositories() {
        return maxEnabledRepositories;
    }

    public void setMaxEnabledRepositories(Integer maxEnabledRepositories) {
        this.maxEnabledRepositories = maxEnabledRepositories;
    }

    public List<GitHubAccessSourceDto> getAccessSources() {
        return accessSources;
    }

    public void setAccessSources(List<GitHubAccessSourceDto> accessSources) {
        this.accessSources = accessSources;
    }

    public List<ProjectRepositoryLinkDto> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<ProjectRepositoryLinkDto> repositories) {
        this.repositories = repositories;
    }
}
