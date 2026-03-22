package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import java.util.UUID;

public interface ProjectService {
    ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl);

    ProjectGitHubPreviewDto getGitHubPreview(UUID projectId, String repositoryUrl);

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getGitHubActivityPage(
        UUID projectId,
        String repositoryUrl,
        int page,
        int size
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getGitHubContributorsPage(
        UUID projectId,
        String repositoryUrl,
        int page,
        int size
    );

    void onRepositoryUrlUpdated(UUID projectId, String repositoryUrl);

    void switchToManualRepository(UUID projectId, String repositoryUrl);

    void clearGitHubLinkage(UUID projectId);

    void linkGitHubInstallation(
        UUID projectId,
        String repositoryUrl,
        Long installationId,
        String ownerLogin
    );

    void refreshGitHubData(UUID projectId, String repositoryUrl);
}
