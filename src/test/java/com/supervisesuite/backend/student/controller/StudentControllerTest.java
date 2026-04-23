package com.supervisesuite.backend.student.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.auth.dto.ChangePasswordRequest;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.meetings.dto.CreateMeetingChannelRequest;
import com.supervisesuite.backend.meetings.dto.MeetingChannelDto;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.student.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
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
class StudentControllerTest {

    @Mock
    private StudentService studentService;

    @Mock
    private ApiResponseFactory apiResponseFactory;
    @Mock
    private AuthService authService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    private StudentController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentController(studentService, authService, apiResponseFactory);
        when(authentication.getName()).thenReturn("student-id");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void changePassword_delegatesToAuthServiceAndFactory() {
        ChangePasswordRequest body = new ChangePasswordRequest();
        body.setCurrentPassword("Old1234!");
        body.setNewPassword("New1234!");
        ResponseEntity<ApiResponse<Void>> expected = ResponseEntity.ok(new ApiResponse<>());
        when(apiResponseFactory.ok("Password updated successfully.", null, request)).thenReturn((ResponseEntity) expected);

        ResponseEntity<ApiResponse<Void>> response = controller.changePassword(authentication, body, request);

        assertThat(response).isSameAs(expected);
        verify(authService).changePassword("student-id", body);
    }

    @Test
    void getProjects_delegatesToServiceAndFactory() {
        List<StudentProjectSummaryDto> data = List.of(new StudentProjectSummaryDto());
        ResponseEntity<ApiResponse<List<StudentProjectSummaryDto>>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjects("student-id")).thenReturn(data);
        when(apiResponseFactory.ok("Projects loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<List<StudentProjectSummaryDto>>> response = controller.getProjects(authentication, request);

        assertThat(response).isSameAs(expected);
        verify(studentService).getProjects("student-id");
        verify(apiResponseFactory).ok("Projects loaded.", data, request);
    }

    @Test
    void getProjectById_delegatesToServiceAndFactory() {
        StudentProjectDetailDto data = new StudentProjectDetailDto();
        ResponseEntity<ApiResponse<StudentProjectDetailDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjectById("student-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("Project loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<StudentProjectDetailDto>> response = controller.getProjectById(
            authentication,
            "project-1",
            request
        );

        assertThat(response).isSameAs(expected);
        verify(studentService).getProjectById("student-id", "project-1");
    }

    @Test
    void getProjectGitHubDashboard_usesConnectedMessageWhenRepositoryLinked() {
        ProjectGitHubDashboardDto data = new ProjectGitHubDashboardDto();
        data.setRepositoryLinked(true);
        ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjectGitHubDashboard("student-id", "project-1", null)).thenReturn(data);
        when(apiResponseFactory.ok("GitHub dashboard loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> response = controller.getProjectGitHubDashboard(
            authentication,
            "project-1",
            null,
            request
        );

        assertThat(response).isSameAs(expected);
        verify(apiResponseFactory).ok("GitHub dashboard loaded.", data, request);
    }

    @Test
    void getProjectMeetingChannels_delegatesToServiceAndFactory() {
        List<MeetingChannelDto> data = List.of(
            new MeetingChannelDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZOOM",
                "Weekly sync",
                "https://example.com",
                UUID.randomUUID(),
                "Student",
                "STUDENT",
                "PENDING",
                null,
                null,
                null,
                null,
                null
            )
        );
        ResponseEntity<ApiResponse<List<MeetingChannelDto>>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjectMeetingChannels("student-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("Meeting channels loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<List<MeetingChannelDto>>> response = controller.getProjectMeetingChannels(
            authentication,
            "project-1",
            request
        );

        assertThat(response).isSameAs(expected);
        verify(studentService).getProjectMeetingChannels("student-id", "project-1");
    }

    @Test
    void addProjectMeetingChannel_delegatesToServiceAndFactory() {
        CreateMeetingChannelRequest body = new CreateMeetingChannelRequest();
        body.setPlatform("WHATSAPP");
        body.setChannelName("Group chat");
        body.setLinkOrIdentifier("Group-123");
        MeetingChannelDto data = new MeetingChannelDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "WHATSAPP",
            "Group chat",
            "Group-123",
            UUID.randomUUID(),
            "Student",
            "STUDENT",
            "PENDING",
            null,
            null,
            null,
            null,
            null
        );
        ResponseEntity<ApiResponse<MeetingChannelDto>> expected = ResponseEntity.status(201).body(new ApiResponse<>());

        when(studentService.addProjectMeetingChannel("student-id", "project-1", body)).thenReturn(data);
        when(apiResponseFactory.created("Meeting channel submitted successfully.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<MeetingChannelDto>> response = controller.addProjectMeetingChannel(
            authentication,
            "project-1",
            body,
            request
        );

        assertThat(response).isSameAs(expected);
        verify(studentService).addProjectMeetingChannel("student-id", "project-1", body);
    }

    @Test
    void getProjectGitHubActivity_whenSizeNull_passesZeroToService() {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> data =
            new ProjectGitHubPageDto<>(List.of(), 1, 10, 0, false);
        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> expected =
            ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjectGitHubActivityPage("student-id", "project-1", null, 2, 0)).thenReturn(data);
        when(apiResponseFactory.ok("GitHub activity page loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> response =
            controller.getProjectGitHubActivity(authentication, "project-1", null, 2, null, request);

        assertThat(response).isSameAs(expected);
        verify(studentService).getProjectGitHubActivityPage("student-id", "project-1", null, 2, 0);
    }

    @Test
    void getProjectGitHubContributors_whenSizeProvided_passesThrough() {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> data =
            new ProjectGitHubPageDto<>(List.of(), 1, 10, 0, false);
        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor>>> expected =
            ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getProjectGitHubContributorsPage("student-id", "project-1", null, 3, 25)).thenReturn(data);
        when(apiResponseFactory.ok("GitHub contributors page loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor>>> response =
            controller.getProjectGitHubContributors(authentication, "project-1", null, 3, 25, request);

        assertThat(response).isSameAs(expected);
        verify(studentService).getProjectGitHubContributorsPage("student-id", "project-1", null, 3, 25);
    }

    @Test
    void getProjectJiraHealth_delegatesToServiceAndFactory() {
        JiraHealthDto data = new JiraHealthDto(
            66.7,
            3,
            1,
            1,
            new JiraHealthDto.StatusBreakdown(1, 2, 6),
            List.of(),
            33.3,
            null
        );
        ResponseEntity<ApiResponse<JiraHealthDto>> expected = ResponseEntity.ok(new ApiResponse<>());

        when(studentService.getJiraHealthOverview("student-id", "project-1")).thenReturn(data);
        when(apiResponseFactory.ok("Jira health overview loaded.", data, request)).thenReturn(expected);

        ResponseEntity<ApiResponse<JiraHealthDto>> response =
            controller.getProjectJiraHealth(authentication, "project-1", request);

        assertThat(response).isSameAs(expected);
        verify(studentService).getJiraHealthOverview("student-id", "project-1");
    }
}
