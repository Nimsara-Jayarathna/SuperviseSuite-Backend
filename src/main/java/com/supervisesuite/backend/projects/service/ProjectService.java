package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPreviewDto;
import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl);
    ProjectGitHubDashboardDto getGitHubDashboard(UUID projectId, String repositoryUrl);

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

    void switchToManualRepository(UUID projectId, String repositoryUrl);

    void clearGitHubLinkage(UUID projectId);

    void linkGitHubInstallation(
        UUID projectId,
        String repositoryUrl,
        Long installationId,
        String ownerLogin
    );

    List<GitHubInstallationRepositoryDto> getInstallationRepositories(
        UUID projectId,
        Long installationId,
        UUID supervisorUserId
    );

    ProjectGitHubRepositoryLinkDto linkProjectToInstallationRepository(
        UUID projectId,
        Long installationId,
        Long repositoryId,
        UUID supervisorUserId
    );

    void refreshGitHubData(UUID projectId, String repositoryUrl);
}
