package com.supervisesuite.backend.student.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.student.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.supervisesuite.backend.projects.dto.ProjectCommitActivityDto;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final ApiResponseFactory apiResponseFactory;

    public StudentController(StudentService studentService, ApiResponseFactory apiResponseFactory) {
        this.studentService = studentService;
        this.apiResponseFactory = apiResponseFactory;
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

    @GetMapping("/projects/{projectId}/commits")
    public ResponseEntity<ApiResponse<ProjectCommitActivityDto>> getProjectCommits(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        ProjectCommitActivityDto data = studentService.getProjectCommits(authentication.getName(), projectId);
        String message = data.isRepositoryLinked() ? "Commit activity loaded." : "No repository connected.";
        return apiResponseFactory.ok(message, data, request);
    }
}
