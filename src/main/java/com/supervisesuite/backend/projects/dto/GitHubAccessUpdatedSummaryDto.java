package com.supervisesuite.backend.projects.dto;

import java.util.List;
import java.util.UUID;

public class GitHubAccessUpdatedSummaryDto {

    private UUID projectId;
    private String projectTitle;
    private Long installationId;
    private String sourceId;
    private String flowType;
    private String accessScope;
    private Integer accessibleRepositoryCount;
    private List<GitHubInstallationRepositoryDto> repositories;

    public GitHubAccessUpdatedSummaryDto() {
    }

    public GitHubAccessUpdatedSummaryDto(
        UUID projectId,
        String projectTitle,
        Long installationId,
        String accessScope,
        Integer accessibleRepositoryCount,
        List<GitHubInstallationRepositoryDto> repositories
    ) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.installationId = installationId;
        this.accessScope = accessScope;
        this.accessibleRepositoryCount = accessibleRepositoryCount;
        this.repositories = repositories;
    }

    public GitHubAccessUpdatedSummaryDto(
        UUID projectId,
        String projectTitle,
        Long installationId,
        String sourceId,
        String flowType,
        String accessScope,
        Integer accessibleRepositoryCount,
        List<GitHubInstallationRepositoryDto> repositories
    ) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.installationId = installationId;
        this.sourceId = sourceId;
        this.flowType = flowType;
        this.accessScope = accessScope;
        this.accessibleRepositoryCount = accessibleRepositoryCount;
        this.repositories = repositories;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public String getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(String accessScope) {
        this.accessScope = accessScope;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public Integer getAccessibleRepositoryCount() {
        return accessibleRepositoryCount;
    }

    public void setAccessibleRepositoryCount(Integer accessibleRepositoryCount) {
        this.accessibleRepositoryCount = accessibleRepositoryCount;
    }

    public List<GitHubInstallationRepositoryDto> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<GitHubInstallationRepositoryDto> repositories) {
        this.repositories = repositories;
    }
}
