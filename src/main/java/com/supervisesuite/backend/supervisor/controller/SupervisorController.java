package com.supervisesuite.backend.supervisor.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMembersRequest;
import com.supervisesuite.backend.supervisor.dto.AddSupervisorProjectMilestoneRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.SupervisorDashboardDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectDetailDto;
import com.supervisesuite.backend.supervisor.dto.SupervisorProjectSummaryDto;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.projects.dto.UpdateRepositoryRequest;
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
import com.supervisesuite.backend.projects.dto.ProjectCommitActivityDto;

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

    
@GetMapping("/projects/{projectId}/commits")
public ResponseEntity<ApiResponse<ProjectCommitActivityDto>> getProjectCommits(
    Authentication authentication,
    @PathVariable String projectId
 ) {
     ProjectCommitActivityDto data = supervisorService.getProjectCommitActivity(authentication.getName(), projectId);
     String message = data.isRepositoryLinked() ? "Commit activity loaded." : "No repository connected.";

    return ResponseEntity.ok(new ApiResponse<>(
      true,
      message,
      data,
      null
    ));
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
