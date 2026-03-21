package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;

public interface ProjectService {
    ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl);

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getGitHubActivityPage(
        String repositoryUrl,
        int page,
        int size
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getGitHubContributorsPage(
        String repositoryUrl,
        int page,
        int size
    );
}
