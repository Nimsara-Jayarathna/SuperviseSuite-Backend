package com.supervisesuite.backend.supervisor.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
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
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    public SupervisorController(SupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<SupervisorDashboardDto>> getDashboard(
        Authentication authentication
    ) {
        SupervisorDashboardDto data = supervisorService.getDashboard(authentication.getName());

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Dashboard loaded.",
            data,
            null
        ));
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<SupervisorProjectSummaryDto>>> getProjects(
        Authentication authentication
    ) {
        List<SupervisorProjectSummaryDto> data = supervisorService.getProjects(authentication.getName());

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Projects loaded.",
            data,
            null
        ));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> getProjectById(
        Authentication authentication,
        @PathVariable String projectId
    ) {
        SupervisorProjectDetailDto data = supervisorService.getProjectById(authentication.getName(), projectId);

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Project loaded.",
            data,
            null
        ));
    }

    @GetMapping("/students/search")
    public ResponseEntity<ApiResponse<List<StudentSearchResultDto>>> searchStudents(
        @RequestParam(name = "q", defaultValue = "") String query
    ) {
        List<StudentSearchResultDto> data = supervisorService.searchStudents(query);

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            data.isEmpty() ? "No matching students found." : "Students loaded.",
            data,
            null
        ));
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<CreateSupervisorProjectResponse>> createProject(
        Authentication authentication,
        @Valid @RequestBody CreateSupervisorProjectRequest request
    ) {
        CreateSupervisorProjectResponse data = supervisorService.createProject(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
            true,
            "Project created successfully.",
            data,
            null
        ));
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProject(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateSupervisorProjectRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProject(authentication.getName(), projectId, request);

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Project updated successfully.",
            data,
            null
        ));
    }

    @PatchMapping("/projects/{projectId}/status")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProjectStatus(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateSupervisorProjectStatusRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProjectStatus(
            authentication.getName(),
            projectId,
            request
        );

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Project status updated successfully.",
            data,
            null
        ));
    }

    @PatchMapping("/projects/{projectId}/repository")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateRepository(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody UpdateRepositoryRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateRepository(
            authentication.getName(),
            projectId,
            request
        );

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Project repository updated successfully.",
            data,
            null
        ));
    }

    @PostMapping("/projects/{projectId}/members")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> addProjectMembers(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody AddSupervisorProjectMembersRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.addProjectMembers(
            authentication.getName(),
            projectId,
            request
        );

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Students added successfully.",
            data,
            null
        ));
    }

    @PostMapping("/projects/{projectId}/milestones")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> addProjectMilestone(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody AddSupervisorProjectMilestoneRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.addProjectMilestone(
            authentication.getName(),
            projectId,
            request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(
            true,
            "Milestone added successfully.",
            data,
            null
        ));
    }

    @PatchMapping("/projects/{projectId}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<SupervisorProjectDetailDto>> updateProjectMilestone(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String milestoneId,
        @Valid @RequestBody UpdateSupervisorProjectMilestoneRequest request
    ) {
        SupervisorProjectDetailDto data = supervisorService.updateProjectMilestone(
            authentication.getName(),
            projectId,
            milestoneId,
            request
        );

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Milestone updated successfully.",
            data,
            null
        ));
    }
}
