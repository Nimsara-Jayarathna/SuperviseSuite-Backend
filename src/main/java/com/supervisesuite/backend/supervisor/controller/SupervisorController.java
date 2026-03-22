package com.supervisesuite.backend.supervisor.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.projects.dto.GitHubInstallationRepositoryPageDto;
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
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.UpdateSupervisorProjectStatusRequest;
import com.supervisesuite.backend.supervisor.service.SupervisorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supervisor")
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorController {

    private final SupervisorService supervisorService;
    private final ApiResponseFactory apiResponseFactory;

    public SupervisorController(SupervisorService supervisorService, ApiResponseFactory apiResponseFactory) {
        this.supervisorService = supervisorService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<SupervisorDashboardDto>> getDashboard(
        Authentication authentication,
        HttpServletRequest request
    ) {
        SupervisorDashboardDto data = supervisorService.getDashboard(authentication.getName());
        return apiResponseFactory.ok("Dashboard loaded.", data, request);
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<SupervisorProjectSummaryDto>>> getProjects(
        Authentication authentication,
        HttpServletRequest request
    ) {
        List<SupervisorProjectSummaryDto> data = supervisorService.getProjects(authentication.getName());
        return apiResponseFactory.ok("Projects loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> getProjectById(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.getProjectById(authentication.getName(), projectId);
        return apiResponseFactory.ok("Project loaded.", data, request);
    }

    @GetMapping("/students/search")
    public ResponseEntity<ApiResponse<List<StudentSearchResultDto>>> searchStudents(
        HttpServletRequest request,
        @RequestParam(name = "q", defaultValue = "") String query
    ) {
        List<StudentSearchResultDto> data = supervisorService.searchStudents(query);
        return apiResponseFactory.ok(
            data.isEmpty() ? "No matching students found." : "Students loaded.",
            data,
            request
        );
    }

    @GetMapping("/projects/{projectId}/github")
    public ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> getProjectGitHubDashboard(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId
    ) {
        ProjectGitHubDashboardDto data = supervisorService.getProjectGitHubDashboard(
            authentication.getName(),
            projectId
        );
        String message = data.isRepositoryLinked() ? "GitHub dashboard loaded." : "No repository connected.";
        return apiResponseFactory.ok(message, data, request);
    }

    @GetMapping("/projects/{projectId}/github/activity")
    public ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> getProjectGitHubActivity(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", required = false) Integer size
    ) {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> data = supervisorService.getProjectGitHubActivityPage(
            authentication.getName(),
            projectId,
            page,
            size == null ? 0 : size
        );
        return apiResponseFactory.ok("GitHub activity page loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/github/contributors")
    public ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor>>> getProjectGitHubContributors(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", required = false) Integer size
    ) {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> data = supervisorService.getProjectGitHubContributorsPage(
            authentication.getName(),
            projectId,
            page,
            size == null ? 0 : size
        );
        return apiResponseFactory.ok("GitHub contributors page loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/github/installations/{installationId}/repositories")
    public ResponseEntity<ApiResponse<GitHubInstallationRepositoryPageDto>> getGitHubInstallationRepositories(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @PathVariable Long installationId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", required = false) Integer size
    ) {
        GitHubInstallationRepositoryPageDto data = supervisorService.getGitHubInstallationRepositories(
            authentication.getName(),
            projectId,
            installationId,
            page,
            size
        );
        return apiResponseFactory.ok("GitHub installation repositories loaded.", data, request);
    }

    @PostMapping("/projects/{projectId}/github/link")
    public ResponseEntity<ApiResponse<ProjectGitHubRepositoryLinkDto>> linkProjectGitHubRepository(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody LinkProjectGitHubRepositoryRequest body
    ) {
        ProjectGitHubRepositoryLinkDto data = supervisorService.linkProjectGitHubRepository(
            authentication.getName(),
            projectId,
            body
        );
        return apiResponseFactory.ok("GitHub repository linked successfully.", data, request);
    }

    @PostMapping("/projects/{projectId}/github/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshProjectGitHub(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId
    ) {
        supervisorService.refreshProjectGitHubData(authentication.getName(), projectId);
        return apiResponseFactory.ok("GitHub data refreshed successfully.", null, request);
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<CreateSupervisorProjectResponse>> createProject(
        Authentication authentication,
        HttpServletRequest httpRequest,
        @Valid @RequestBody CreateSupervisorProjectRequest body
    ) {
        CreateSupervisorProjectResponse data = supervisorService.createProject(
            authentication.getName(),
            body
        );
        return apiResponseFactory.created("Project created successfully.", data, httpRequest);
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProject(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateSupervisorProjectRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProject(authentication.getName(), projectId, body);
        return apiResponseFactory.ok("Project updated successfully.", data, request);
    }

    @PatchMapping("/projects/{projectId}/status")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProjectStatus(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateSupervisorProjectStatusRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProjectStatus(
            authentication.getName(),
            projectId,
            body
        );
        return apiResponseFactory.ok("Project status updated successfully.", data, request);
    }

    @PatchMapping("/projects/{projectId}/repository")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateRepository(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateRepositoryRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateRepository(
            authentication.getName(),
            projectId,
            body
        );
        return apiResponseFactory.ok("Project repository updated successfully.", data, request);
    }

    @PostMapping("/projects/{projectId}/members")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> addProjectMembers(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody AddSupervisorProjectMembersRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.addProjectMembers(
            authentication.getName(),
            projectId,
            body
        );
        return apiResponseFactory.ok("Students added successfully.", data, request);
    }

    @PostMapping("/projects/{projectId}/milestones")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> addProjectMilestone(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @Valid @RequestBody AddSupervisorProjectMilestoneRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.addProjectMilestone(
            authentication.getName(),
            projectId,
            body
        );
        return apiResponseFactory.created("Milestone added successfully.", data, request);
    }

    @PatchMapping("/projects/{projectId}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProjectMilestone(
        Authentication authentication,
        HttpServletRequest request,
        @PathVariable String projectId,
        @PathVariable String milestoneId,
        @Valid @RequestBody UpdateSupervisorProjectMilestoneRequest body
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProjectMilestone(
            authentication.getName(),
            projectId,
            milestoneId,
            body
        );
        return apiResponseFactory.ok("Milestone updated successfully.", data, request);
    }
}
