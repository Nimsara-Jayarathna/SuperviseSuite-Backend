package com.supervisesuite.backend.student.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.student.dto.StudentProjectDetailDto;
import com.supervisesuite.backend.student.dto.StudentProjectSummaryDto;
import com.supervisesuite.backend.student.service.StudentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<StudentProjectSummaryDto>>> getProjects(
        Authentication authentication
    ) {
        List<StudentProjectSummaryDto> data = studentService.getProjects(authentication.getName());

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Projects loaded.",
            data,
            null
        ));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<StudentProjectDetailDto>> getProjectById(
        Authentication authentication,
        @PathVariable String projectId
    ) {
        StudentProjectDetailDto data = studentService.getProjectById(authentication.getName(), projectId);

        return ResponseEntity.ok(new ApiResponse<>(
            true,
            "Project loaded.",
            data,
            null
        ));
    }
}
