package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;

public interface ProjectService {
    ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl);
}
