package com.supervisesuite.backend.student.service;

import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import java.util.List;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;

public interface StudentService {
    List<StudentProjectSummaryDto> getProjects(String authenticatedUserId);

    StudentProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

    ProjectGitHubDashboardDto getProjectGitHubDashboard(String authenticatedUserId, String projectId);

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
        String authenticatedUserId,
        String projectId,
        int page,
        int size
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
        String authenticatedUserId,
        String projectId,
        int page,
        int size
    );
}
