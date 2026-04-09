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
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestContinueDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestValidationDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryListingDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedSummaryDto;
import com.supervisesuite.backend.projects.dto.GitHubAccessUpdatedAcknowledgeDto;
import com.supervisesuite.backend.projects.dto.JiraAuthUrlDto;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteRequestDto;
import com.supervisesuite.backend.projects.dto.JiraOAuthCompleteResultDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import java.util.List;

public interface SupervisorService {
    SupervisorDashboardDto getDashboard(String authenticatedUserId);

    List<SupervisorProjectSummaryDto> getProjects(String authenticatedUserId);

    SupervisorProjectDetailDto getProjectById(String authenticatedUserId, String projectId);

    ProjectGitHubDashboardDto getProjectGitHubDashboard(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getProjectGitHubActivityPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
        int page,
        int size
    );

    ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getProjectGitHubContributorsPage(
        String authenticatedUserId,
        String projectId,
        String linkedRepositoryId,
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

    ProjectGitHubRepositoryListingDto getProjectRepositoriesInventory(
        String authenticatedUserId,
        String projectId
    );

    GitHubAccessRequestCreateDto createGitHubRepositoryAccessRequest(
        String authenticatedUserId,
        String projectId
    );

    GitHubAccessRequestValidationDto validateGitHubRepositoryAccessRequest(
        String authenticatedUserId,
        String projectId,
        String requestToken
    );

    GitHubAccessRequestContinueDto continueGitHubRepositoryAccessRequest(
        String authenticatedUserId,
        String projectId,
        String requestToken
    );

    String buildGitHubSetupStartUrl(
        String authenticatedUserId,
        String projectId
    );

    ProjectGitHubRepositoryLinkDto linkProjectGitHubRepository(
        String authenticatedUserId,
        String projectId,
        LinkProjectGitHubRepositoryRequest request
    );

    SupervisorProjectDetailDto removeProjectGitHubAccessAuthorization(
        String authenticatedUserId,
        String projectId
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

    GitHubAccessUpdatedSummaryDto getGitHubAccessUpdatedSummary(
        String authenticatedUserId,
        String projectId
    );

    GitHubAccessUpdatedAcknowledgeDto acknowledgeGitHubAccessUpdated(
        String authenticatedUserId,
        String projectId
    );

    JiraAuthUrlDto getProjectJiraAuthUrl(String authenticatedUserId, String projectId);

    JiraOAuthCompleteResultDto completeJiraOAuth(String authenticatedUserId, JiraOAuthCompleteRequestDto request);

    SupervisorProjectDetailDto disconnectProjectJira(String authenticatedUserId, String projectId);

    JiraHealthDto getJiraHealthOverview(String authenticatedUserId, String projectId);

    JiraSprintProgressDto getJiraSprintProgress(String authenticatedUserId, String projectId);

    JiraHealthDto refreshProjectJiraData(String authenticatedUserId, String projectId);
}
