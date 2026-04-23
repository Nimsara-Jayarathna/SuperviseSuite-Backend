package com.supervisesuite.backend.projectfiles.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlRequest;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlResponse;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import com.supervisesuite.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/projects/{projectId}/files")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Project Files", description = "Student project file management.")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH_SCHEME)
public class StudentProjectFilesController {

    private final ProjectFileService projectFileService;
    private final ApiResponseFactory apiResponseFactory;

    public StudentProjectFilesController(ProjectFileService projectFileService, ApiResponseFactory apiResponseFactory) {
        this.projectFileService = projectFileService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @GetMapping
    @Operation(summary = "List project files (student)")
    public ResponseEntity<ApiResponse<ProjectFileListDto>> list(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        ProjectFileListDto data = projectFileService.listFiles(
            authentication.getName(),
            projectId,
            ProjectFileAccessRole.STUDENT
        );
        return apiResponseFactory.ok("Project files loaded.", data, request);
    }

    @PostMapping("/upload-url")
    @Operation(summary = "Create an upload URL (student)")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody UploadUrlRequest body,
        HttpServletRequest request
    ) {
        UploadUrlResponse data = projectFileService.getUploadUrl(
            authentication.getName(),
            projectId,
            ProjectFileAccessRole.STUDENT,
            body
        );
        return apiResponseFactory.ok("Upload URL generated.", data, request);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm uploaded file (student)")
    public ResponseEntity<ApiResponse<ProjectFileDto>> confirmUpload(
        Authentication authentication,
        @PathVariable String projectId,
        @Valid @RequestBody ConfirmUploadRequest body,
        HttpServletRequest request
    ) {
        ProjectFileDto data = projectFileService.confirmUpload(
            authentication.getName(),
            projectId,
            ProjectFileAccessRole.STUDENT,
            body
        );
        return apiResponseFactory.ok("File upload confirmed.", data, request);
    }

    @GetMapping("/{fileId}/download-url")
    @Operation(summary = "Create a download URL (student)")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String fileId,
        HttpServletRequest request
    ) {
        String data = projectFileService.getDownloadUrl(
            authentication.getName(),
            projectId,
            fileId,
            ProjectFileAccessRole.STUDENT
        );
        return apiResponseFactory.ok("Download URL generated.", data, request);
    }
}
