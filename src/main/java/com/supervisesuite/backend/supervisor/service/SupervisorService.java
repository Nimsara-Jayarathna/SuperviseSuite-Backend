package com.supervisesuite.backend.supervisor.service;

import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import java.util.List;

public interface SupervisorService {
    SupervisorDashboardDto getDashboard(String authenticatedUserId);

    List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId);

    SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

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

    GitHubInstallationRepositoryPageDto getGitHubInstallationRepositories(
        String authenticatedUserId,
        String projectId,
        Long installationId,
        int page,
        Integer size
    );

    ProjectGitHubRepositoryLinkDto linkProjectGitHubRepository(
        String authenticatedUserId,
        String projectId,
        LinkProjectGitHubRepositoryRequest request
    );

    void refreshProjectGitHubData(String authenticatedUserId, String projectId);

    SupervisorProjectDetailDto updateProject(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectRequest request
    );

    SupervisorProjectDetailDto updateProjectStatus(
        String authenticatedUserId,
        String projectId,
        UpdateSupervisorProjectStatusRequest request
    );

    SupervisorProjectDetailDto updateRepository(
        String authenticatedUserId,
        String projectId,
        UpdateRepositoryRequest request
    );

    SupervisorProjectDetailDto addProjectMembers(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMembersRequest request
    );

    SupervisorProjectDetailDto addProjectMilestone(
        String authenticatedUserId,
        String projectId,
        AddSupervisorProjectMilestoneRequest request
    );

    SupervisorProjectDetailDto updateProjectMilestone(
        String authenticatedUserId,
        String projectId,
        String milestoneId,
        UpdateSupervisorProjectMilestoneRequest request
    );

    List<StudentSearchResultDto> searchStudents(String query);

    CreateSupervisorProjectResponse createProject(
        String authenticatedUserId,
        CreateSupervisorProjectRequest request
    );
}
