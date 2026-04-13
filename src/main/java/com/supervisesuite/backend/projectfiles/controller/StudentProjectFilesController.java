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
public class StudentProjectFilesController {

    private final ProjectFileService projectFileService;
    private final ApiResponseFactory apiResponseFactory;

    public StudentProjectFilesController(ProjectFileService projectFileService, ApiResponseFactory apiResponseFactory) {
        this.projectFileService = projectFileService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @GetMapping
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
