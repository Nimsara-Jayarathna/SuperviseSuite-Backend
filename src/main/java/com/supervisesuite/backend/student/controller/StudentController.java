package com.supervisesuite.backend.student.controller;

import com.supervisesuite.backend.auth.dto.ChangePasswordRequest;
import com.supervisesuite.backend.auth.service.AuthService;
import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.dto.JiraHierarchyDto;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.dto.JiraWorkloadDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.student.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final AuthService authService;
    private final ApiResponseFactory apiResponseFactory;

    public StudentController(StudentService studentService, AuthService authService, ApiResponseFactory apiResponseFactory) {
        this.studentService = studentService;
        this.authService = authService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        Authentication authentication,
        @Valid @RequestBody ChangePasswordRequest body,
        HttpServletRequest request
    ) {
        authService.changePassword(authentication.getName(), body);
        return apiResponseFactory.ok("Password updated successfully.", null, request);
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<StudentProjectSummaryDto>>> getProjects(
        Authentication authentication,
        HttpServletRequest request
    ) {
        List<StudentProjectSummaryDto> data = studentService.getProjects(authentication.getName());
        return apiResponseFactory.ok("Projects loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<StudentProjectDetailDto>> getProjectById(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        StudentProjectDetailDto data = studentService.getProjectById(authentication.getName(), projectId);
        return apiResponseFactory.ok("Project loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/github")
    public ResponseEntity<ApiResponse<ProjectGitHubDashboardDto>> getProjectGitHubDashboard(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(name = "linkedRepositoryId", required = false) String linkedRepositoryId,
        HttpServletRequest request
    ) {
        ProjectGitHubDashboardDto data = studentService.getProjectGitHubDashboard(
            authentication.getName(),
            projectId,
            linkedRepositoryId
        );
        String message = data.isRepositoryLinked() ? "GitHub dashboard loaded." : "No repository connected.";
        return apiResponseFactory.ok(message, data, request);
    }

    @GetMapping("/projects/{projectId}/github/activity")
    public ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit>>> getProjectGitHubActivity(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(name = "linkedRepositoryId", required = false) String linkedRepositoryId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", required = false) Integer size,
        HttpServletRequest request
    ) {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> data = studentService.getProjectGitHubActivityPage(
            authentication.getName(),
            projectId,
            linkedRepositoryId,
            page,
            size == null ? 0 : size
        );
        return apiResponseFactory.ok("GitHub activity page loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/github/contributors")
    public ResponseEntity<ApiResponse<ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor>>> getProjectGitHubContributors(
        Authentication authentication,
        @PathVariable String projectId,
        @RequestParam(name = "linkedRepositoryId", required = false) String linkedRepositoryId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", required = false) Integer size,
        HttpServletRequest request
    ) {
        ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> data = studentService.getProjectGitHubContributorsPage(
            authentication.getName(),
            projectId,
            linkedRepositoryId,
            page,
            size == null ? 0 : size
        );
        return apiResponseFactory.ok("GitHub contributors page loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/jira/health")
    public ResponseEntity<ApiResponse<JiraHealthDto>> getProjectJiraHealth(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        JiraHealthDto data = studentService.getJiraHealthOverview(authentication.getName(), projectId);
        return apiResponseFactory.ok("Jira health overview loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/jira/sprint-progress")
    public ResponseEntity<ApiResponse<JiraSprintProgressDto>> getProjectJiraSprintProgress(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        JiraSprintProgressDto data = studentService.getJiraSprintProgress(authentication.getName(), projectId);
        return apiResponseFactory.ok("Jira sprint progress loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/jira/workload")
    public ResponseEntity<ApiResponse<JiraWorkloadDto>> getProjectJiraWorkload(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        JiraWorkloadDto data = studentService.getJiraWorkload(authentication.getName(), projectId);
        return apiResponseFactory.ok("Jira team workload loaded.", data, request);
    }

    @GetMapping("/projects/{projectId}/jira/hierarchy")
    public ResponseEntity<ApiResponse<JiraHierarchyDto>> getProjectJiraHierarchy(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        JiraHierarchyDto data = studentService.getJiraHierarchy(authentication.getName(), projectId);
        return apiResponseFactory.ok("Jira hierarchy loaded.", data, request);
    }
}
