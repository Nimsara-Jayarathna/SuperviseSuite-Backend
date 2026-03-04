package com.supervisesuite.backend.supervisor.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectRequest;
import com.supervisesuite.backend.supervisor.dto.CreateSupervisorProjectResponse;
import com.supervisesuite.backend.supervisor.dto.StudentSearchResultDto;
import com.supervisesuite.backend.supervisor.service.SupervisorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
}
