package com.supervisesuite.backend.projectfiles.service;

import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlRequest;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlResponse;
import java.util.List;

public interface ProjectFileService {
    List<ProjectFileDto> listFiles(String authenticatedUserId, String projectId, ProjectFileAccessRole accessRole);

    UploadUrlResponse getUploadUrl(
        String authenticatedUserId,
        String projectId,
        ProjectFileAccessRole accessRole,
        UploadUrlRequest request
    );

    ProjectFileDto confirmUpload(
        String authenticatedUserId,
        String projectId,
        ProjectFileAccessRole accessRole,
        ConfirmUploadRequest request
    );

    String getDownloadUrl(
        String authenticatedUserId,
        String projectId,
        String fileId,
        ProjectFileAccessRole accessRole
    );

    void deleteFile(String authenticatedUserId, String projectId, String fileId);
}
