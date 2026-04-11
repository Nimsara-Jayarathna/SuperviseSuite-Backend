package com.supervisesuite.backend.projectfiles.controller;

import com.supervisesuite.backend.common.api.ApiResponse;
import com.supervisesuite.backend.common.api.ApiResponseFactory;
import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlRequest;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlResponse;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supervisor/projects/{projectId}/files")
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorProjectFilesController {

    private final ProjectFileService projectFileService;
    private final ApiResponseFactory apiResponseFactory;

    public SupervisorProjectFilesController(ProjectFileService projectFileService, ApiResponseFactory apiResponseFactory) {
        this.projectFileService = projectFileService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectFileDto>>> list(
        Authentication authentication,
        @PathVariable String projectId,
        HttpServletRequest request
    ) {
        List<ProjectFileDto> data = projectFileService.listFiles(
            authentication.getName(),
            projectId,
            ProjectFileAccessRole.SUPERVISOR
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
            ProjectFileAccessRole.SUPERVISOR,
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
            ProjectFileAccessRole.SUPERVISOR,
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
            ProjectFileAccessRole.SUPERVISOR
        );
        return apiResponseFactory.ok("Download URL generated.", data, request);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
        Authentication authentication,
        @PathVariable String projectId,
        @PathVariable String fileId,
        HttpServletRequest request
    ) {
        projectFileService.deleteFile(authentication.getName(), projectId, fileId);
        return apiResponseFactory.ok("File deleted.", null, request);
    }
}
