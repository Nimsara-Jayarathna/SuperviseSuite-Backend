package com.supervisesuite.backend.supervisor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.auth.dto.ChangePasswordRequest;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.projects.dto.GitHubAccessRequestCreateDto;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.LinkProjectGitHubRepositoryRequest;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubRepositoryLinkDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.supervisor.service.SupervisorService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class SupervisorControllerTest {

    @Mock
    private SupervisorService supervisorService;

    @Mock
    private ApiResponseFactory apiResponseFactory;
    @Mock
    private AuthService authService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    private SupervisorController controller;

    @BeforeEach
    void setUp() {
        controller = new SupervisorController(supervisorService, authService, apiResponseFactory);
        when(authentication.getName()).thenReturn("supervisor-id");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void changePassword_delegatesToAuthServiceAndFactory() {
        ChangePasswordRequest body = new ChangePasswordRequest();
        body.setCurrentPassword("Old1234!");
        body.setNewPassword("New1234!");
        ResponseEntity<ApiResponse<Void>> expected = ResponseEntity.ok(new ApiResponse<>());
        when(apiResponseFactory.ok("Password updated successfully.", null, request)).thenReturn((ResponseEntity) expected);

        ResponseEntity<ApiResponse<Void>> response = controller.changePassword(authentication, request, body);

        assertThat(response).isSameAs(expected);
        verify(authService).changePassword("supervisor-id", body);
    }

    @Test
    void getDashboard_delegatesToServiceAndFactory() {
        SupervisorDashboardDto data = new SupervisorDashboardDto();
        ResponseEntity<ApiResponse<SupervisorDashboardDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.getDashboard("supervisor-id")).thenReturn(data);
        when(apiResponseFactory.ok("Dashboard loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<SupervisorDashboardDto>> response = controller.getDashboard(authentication, request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getProjectGitHubDashboard_usesNoRepositoryMessageWhenNotLinked() {
        ProjectGitHubDashboardDto data = new ProjectGitHubDashboardDto();
        data.setRepositoryLinked(false);
        ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.getProjectGitHubDashboard("supervisor-id", "project-1", null)).thenReturn(data);
        when(apiResponseFactory.ok("No repository connected.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> response = controller.getProjectGitHubDashboard(
            authentication,
            request,
            "project-1",
            null
        );

        assertThat(response).isSameAs(expected);
    }

    @Test
    void getProjectGitHubActivity_whenSizeNull_passesZeroToService() {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> data =
            new ProjectGitHubPageDto<>(List.of(), 1, 10, 0, false);
        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> expected =
            ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.getProjectGitHubActivityPage("supervisor-id", "project-1", null, 2, 0)).thenReturn(data);
        when(apiResponseFactory.ok("GitHub activity page loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> response =
            controller.getProjectGitHubActivity(authentication, request, "project-1", null, 2, null);

        assertThat(response).isSameAs(expected);
        verify(supervisorService).getProjectGitHubActivityPage("supervisor-id", "project-1", null, 2, 0);
    }

    @Test
    void createGitHubRepositoryAccessRequest_delegatesToServiceAndFactory() {
        GitHubAccessRequestCreateDto data = new GitHubAccessRequestCreateDto();
        ResponseEntity<ApiResponse<GitHubAccessRequestCreateDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.createGitHubRepositoryAccessRequest("supervisor-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("GitHub repository access request created.", data, request)).thenReturn(expected);

        assertThat(controller.createGitHubRepositoryAccessRequest(authentication, request, "project-1"))
            .isSameAs(expected);
    }

    @Test
    void startGitHubSetup_redirectsToAuthorizeUrlFromService() {
        when(supervisorService.buildGitHubSetupStartUrl("supervisor-id", "project-1"))
            .thenReturn("https://github.com/apps/supervisesuite/installations/new?state=test");

        ResponseEntity<Void> response = controller.startGitHubSetup(authentication, "project-1");

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        assertThat(response.getHeaders().getLocation())
            .hasToString("https://github.com/apps/supervisesuite/installations/new?state=test");
    }

    @Test
    void linkProjectGitHubRepository_delegatesToServiceAndFactory() {
        LinkProjectGitHubRepositoryRequest body = new LinkProjectGitHubRepositoryRequest();
        body.setInstallationId(10L);
        body.setRepositoryId(20L);

        ProjectGitHubRepositoryLinkDto data = new ProjectGitHubRepositoryLinkDto();
        ResponseEntity<ApiResponse<ProjectGitHubRepositoryLinkDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.linkProjectGitHubRepository("supervisor-id", "project-1", body)).thenReturn(data);
        when(apiResponseFactory.ok("GitHub repository linked successfully.", data, request)).thenReturn(expected);

        assertThat(controller.linkProjectGitHubRepository(authentication, request, "project-1", body))
            .isSameAs(expected);
    }

    @Test
    void removeProjectGitHubAccessAuthorization_delegatesToServiceAndFactory() {
        SupervisorProjectDetailDto data = new SupervisorProjectDetailDto();
        ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.removeProjectGitHubAccessAuthorization("supervisor-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("GitHub access authorization removed for this project.", data, request))
            .thenReturn(expected);

        assertThat(controller.removeProjectGitHubAccessAuthorization(authentication, request, "project-1"))
            .isSameAs(expected);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void refreshProjectGitHub_callsServiceAndReturnsOkEnvelope() {
        ResponseEntity<ApiResponse<Void>> expected = ResponseEntity.ok(new ApiResponse<>());
        when(apiResponseFactory.ok("GitHub data refreshed successfully.", null, request)).thenReturn((ResponseEntity) expected);

        ResponseEntity<ApiResponse<Void>> response = controller.refreshProjectGitHub(authentication, request, "project-1");

        assertThat(response).isSameAs(expected);
        verify(supervisorService).refreshProjectGitHubData("supervisor-id", "project-1");
    }

    @Test
    void getProjectJiraHealth_delegatesToServiceAndFactory() {
        JiraHealthDto data = new JiraHealthDto(
            75.0,
            5,
            2,
            1,
            new JiraHealthDto.StatusBreakdown(2, 3, 15),
            List.of(),
            20.0,
            null
        );
        ResponseEntity<ApiResponse<JiraHealthDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.getJiraHealthOverview("supervisor-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("Jira health overview loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<JiraHealthDto>> response =
            controller.getProjectJiraHealth(authentication, request, "project-1");

        assertThat(response).isSameAs(expected);
        verify(supervisorService).getJiraHealthOverview("supervisor-id", "project-1");
    }

    @Test
    void refreshProjectJira_delegatesToServiceAndFactory() {
        JiraHealthDto data = new JiraHealthDto(
            80.0,
            4,
            1,
            1,
            new JiraHealthDto.StatusBreakdown(1, 3, 16),
            List.of(),
            25.0,
            null
        );
        ResponseEntity<ApiResponse<JiraHealthDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.refreshProjectJiraData("supervisor-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("Jira data refreshed successfully.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<JiraHealthDto>> response =
            controller.refreshProjectJira(authentication, request, "project-1");

        assertThat(response).isSameAs(expected);
        verify(supervisorService).refreshProjectJiraData("supervisor-id", "project-1");
    }

    @Test
    void createAndUpdateProjectEndpoints_delegateToServiceAndFactory() {
        CreateSupervisorProjectRequest createBody = new CreateSupervisorProjectRequest();
        createBody.setTitle("Capstone");
        createBody.setSummary("Summary");
        createBody.setBatch("2025");
        createBody.setSemester("Y3S2");
        createBody.setStudentIds(List.of(UUID.randomUUID()));
        CreateSupervisorProjectRequest.InitialMilestone milestone = new CreateSupervisorProjectRequest.InitialMilestone();
        milestone.setTitle("M1");
        milestone.setDueDate(LocalDate.now().plusDays(1));
        createBody.setMilestones(List.of(milestone));

        CreateSupervisorProjectResponse createData = new CreateSupervisorProjectResponse();
        ResponseEntity<ApiResponse<CreateSupervisorProjectResponse>> createExpected = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.createProject("supervisor-id", createBody)).thenReturn(createData);
        when(apiResponseFactory.created("Project created successfully.", createData, request)).thenReturn(createExpected);

        assertThat(controller.createProject(authentication, request, createBody)).isSameAs(createExpected);

        UpdateSupervisorProjectStatusRequest statusBody = new UpdateSupervisorProjectStatusRequest();
        statusBody.setLifecycleStatus("ACTIVE");
        SupervisorProjectDetailDto statusData = new SupervisorProjectDetailDto();
        ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> statusExpected = ResponseEntity.ok(new ApiResponse<>());
        when(supervisorService.updateProjectStatus("supervisor-id", "project-1", statusBody)).thenReturn(statusData);
        when(apiResponseFactory.ok("Project status updated successfully.", statusData, request)).thenReturn(statusExpected);

        assertThat(controller.updateProjectStatus(authentication, request, "project-1", statusBody))
            .isSameAs(statusExpected);
    }

    @Test
    void memberMilestoneAndRepositoryEndpoints_delegateToServiceAndFactory() {
        UpdateRepositoryRequest repositoryBody = new UpdateRepositoryRequest();
        repositoryBody.setRepositoryUrl("https://github.com/acme/repo");
        AddSupervisorProjectMembersRequest membersBody = new AddSupervisorProjectMembersRequest();
        membersBody.setStudentIds(List.of(UUID.randomUUID()));
        AddSupervisorProjectMilestoneRequest addMilestoneBody = new AddSupervisorProjectMilestoneRequest();
        addMilestoneBody.setTitle("M2");
        addMilestoneBody.setDueDate(LocalDate.now().plusDays(7));
        UpdateSupervisorProjectMilestoneRequest updateMilestoneBody = new UpdateSupervisorProjectMilestoneRequest();
        updateMilestoneBody.setTitle("M2");
        updateMilestoneBody.setDueDate(LocalDate.now().plusDays(8));
        updateMilestoneBody.setStatus("IN_PROGRESS");

        SupervisorProjectDetailDto data = new SupervisorProjectDetailDto();
        ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> ok = ResponseEntity.ok(new ApiResponse<>());
        ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> created = ResponseEntity.ok(new ApiResponse<>());

        when(supervisorService.updateRepository("supervisor-id", "project-1", repositoryBody)).thenReturn(data);
        when(supervisorService.addProjectMembers("supervisor-id", "project-1", membersBody)).thenReturn(data);
        when(supervisorService.addProjectMilestone("supervisor-id", "project-1", addMilestoneBody)).thenReturn(data);
        when(supervisorService.updateProjectMilestone("supervisor-id", "project-1", "milestone-1", updateMilestoneBody))
            .thenReturn(data);

        when(apiResponseFactory.ok("Project repository updated successfully.", data, request)).thenReturn(ok);
        when(apiResponseFactory.ok("Students added successfully.", data, request)).thenReturn(ok);
        when(apiResponseFactory.created("Milestone added successfully.", data, request)).thenReturn(created);
        when(apiResponseFactory.ok("Milestone updated successfully.", data, request)).thenReturn(ok);

        assertThat(controller.updateRepository(authentication, request, "project-1", repositoryBody)).isSameAs(ok);
        assertThat(controller.addProjectMembers(authentication, request, "project-1", membersBody)).isSameAs(ok);
        assertThat(controller.addProjectMilestone(authentication, request, "project-1", addMilestoneBody))
            .isSameAs(created);
        assertThat(controller.updateProjectMilestone(
            authentication,
            request,
            "project-1",
            "milestone-1",
            updateMilestoneBody
        )).isSameAs(ok);
    }
}
