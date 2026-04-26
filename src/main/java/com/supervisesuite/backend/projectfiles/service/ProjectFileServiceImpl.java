package com.supervisesuite.backend.projectfiles.service;

import com.supervisesuite.backend.config.ProjectFileProperties;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.common.access.ProjectAccessGuard;
import com.supervisesuite.backend.common.util.EntityIdParser;
import com.supervisesuite.backend.common.util.UserDisplayNameFormatter;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlRequest;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlResponse;
import com.supervisesuite.backend.projectfiles.entity.ProjectFile;
import com.supervisesuite.backend.projectfiles.repository.ProjectFileRepository;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.repository.ProjectRepository;
import com.supervisesuite.backend.storage.StorageService;
import com.supervisesuite.backend.users.entity.User;
import com.supervisesuite.backend.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectFileServiceImpl implements ProjectFileService {

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
        "application/pdf", "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
        "application/zip", "zip",
        "application/x-zip-compressed", "zip"
    );

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectFileRepository projectFileRepository;
    private final StorageService storageService;
    private final ProjectFileProperties projectFileProperties;
    private final ProjectAccessGuard projectAccessGuard;

    ProjectFileServiceImpl(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectFileRepository projectFileRepository,
        StorageService storageService,
        ProjectFileProperties projectFileProperties,
        ProjectAccessGuard projectAccessGuard
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectFileRepository = projectFileRepository;
        this.storageService = storageService;
        this.projectFileProperties = projectFileProperties;
        this.projectAccessGuard = projectAccessGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFileListDto listFiles(String authenticatedUserId, String projectId, ProjectFileAccessRole accessRole) {
        User user = requireAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        List<ProjectFile> files = projectFileRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(parsedProjectId);
        Map<UUID, String> uploadedByRoleByUserId = new HashMap<>();
        userRepository.findAllById(files.stream().map(ProjectFile::getUploadedBy).distinct().toList())
            .forEach(uploadedByUser -> uploadedByRoleByUserId.put(uploadedByUser.getId(), uploadedByUser.getRole()));

        List<ProjectFileDto> fileDtos = files
            .stream()
            .map(projectFile -> toDto(projectFile, uploadedByRoleByUserId.get(projectFile.getUploadedBy())))
            .toList();

        return new ProjectFileListDto(fileDtos, new ProjectFileListDto.Config(
            resolvedMaxFileSizeBytes(),
            resolvedMaxFileNameLength(),
            resolvedAllowedTypes(),
            resolvedPresignedUrlExpirySeconds()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public UploadUrlResponse getUploadUrl(
        String authenticatedUserId,
        String projectId,
        ProjectFileAccessRole accessRole,
        UploadUrlRequest request
    ) {
        User user = requireAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        String fileName = requireTrimmed(request.getFileName(), "fileName");
        validateFileNameLength(fileName, "fileName");
        String contentType = requireTrimmed(request.getContentType(), "contentType");
        validateAllowedFileType(fileName, contentType, "contentType");
        String s3Key = generateS3Key();
        String presignedUrl = storageService.getUploadUrl(s3Key, contentType);
        return new UploadUrlResponse(presignedUrl, s3Key);
    }

    @Override
    @Transactional
    public ProjectFileDto confirmUpload(
        String authenticatedUserId,
        String projectId,
        ProjectFileAccessRole accessRole,
        ConfirmUploadRequest request
    ) {
        User user = requireAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        String s3Key = requireTrimmed(request.getS3Key(), "s3Key");
        String fileName = requireTrimmed(request.getFileName(), "fileName");
        validateFileNameLength(fileName, "fileName");
        String fileType = requireTrimmed(request.getFileType(), "fileType");
        String normalizedFileType = resolveAndValidateAllowedFileType(fileName, fileType, "fileType");
        Long fileSize = request.getFileSize();

        if (!isValidUuidS3Key(s3Key)) {
            throw new ValidationException("s3Key", "s3Key is invalid for this project.");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new ValidationException("fileSize", "fileSize must be greater than zero.");
        }
        if (fileSize > resolvedMaxFileSizeBytes()) {
            throw new ValidationException("fileSize", "fileSize exceeds the maximum allowed size.");
        }

        ProjectFile projectFile = new ProjectFile();
        projectFile.setProjectId(parsedProjectId);
        projectFile.setS3Key(s3Key);
        projectFile.setFileName(fileName);
        projectFile.setFileType(normalizedFileType);
        projectFile.setFileSize(fileSize);
        projectFile.setUploadedBy(user.getId());
        projectFile.setUploadedByName(resolveUserDisplayName(user));

        ProjectFile saved = projectFileRepository.saveAndFlush(projectFile);
        return toDto(saved, user.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(
        String authenticatedUserId,
        String projectId,
        String fileId,
        ProjectFileAccessRole accessRole
    ) {
        User user = requireAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);
        UUID parsedFileId = parseFileId(fileId);

        ProjectFile projectFile = projectFileRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(parsedFileId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        return storageService.getDownloadUrl(projectFile.getS3Key());
    }

    @Override
    @Transactional
    public void deleteFile(String authenticatedUserId, String projectId, String fileId) {
        User supervisor = requireAuthenticatedUser(authenticatedUserId, ProjectFileAccessRole.SUPERVISOR);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(supervisor, parsedProjectId, ProjectFileAccessRole.SUPERVISOR);
        UUID parsedFileId = parseFileId(fileId);

        ProjectFile projectFile = projectFileRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(parsedFileId, parsedProjectId)
            .orElseThrow(EntityNotFoundException::new);

        projectFile.setDeletedAt(Instant.now());
        storageService.delete(projectFile.getS3Key());
        projectFileRepository.save(projectFile);
    }

    private User requireAuthenticatedUser(String authenticatedUserId, ProjectFileAccessRole accessRole) {
        return accessRole == ProjectFileAccessRole.SUPERVISOR
                ? projectAccessGuard.requireSupervisor(authenticatedUserId)
                : projectAccessGuard.requireStudent(authenticatedUserId);
    }

    private Project requireProjectAccess(User user, UUID projectId, ProjectFileAccessRole accessRole) {
        return accessRole == ProjectFileAccessRole.SUPERVISOR
                ? projectAccessGuard.requireSupervisorOwnsProject(user, projectId)
                : projectAccessGuard.requireStudentIsMember(user, projectId);
    }

    private UUID parseProjectId(String projectId) {
        return EntityIdParser.parseOrNotFound(projectId);
    }

    private UUID parseFileId(String fileId) {
        return EntityIdParser.parseOrNotFound(fileId);
    }

    private String requireTrimmed(String value, String field) {
        if (value == null) {
            throw new ValidationException(field, field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(field, field + " is required.");
        }
        return trimmed;
    }

    private void validateFileNameLength(String fileName, String field) {
        int maxFileNameLength = resolvedMaxFileNameLength();
        if (fileName.length() > maxFileNameLength) {
            throw new ValidationException(field, "fileName cannot exceed " + maxFileNameLength + " characters.");
        }
    }

    private void validateAllowedFileType(String fileName, String value, String field) {
        resolveAndValidateAllowedFileType(fileName, value, field);
    }

    private String resolveAndValidateAllowedFileType(String fileName, String value, String field) {
        Set<String> allowedTypes = resolvedAllowedTypesSet();
        String extension = extractExtension(fileName);
        if (extension == null || !allowedTypes.contains(extension)) {
            throw new ValidationException(field, "File type is not allowed.");
        }

        String normalizedMimeType = value == null ? "" : value.trim().toLowerCase();
        if (normalizedMimeType.isEmpty()) {
            return extension;
        }
        String mimeMappedExtension = MIME_TO_EXTENSION.get(normalizedMimeType);
        if (mimeMappedExtension == null || !allowedTypes.contains(mimeMappedExtension)) {
            throw new ValidationException(field, "File type is not allowed.");
        }
        if (!mimeMappedExtension.equals(extension)) {
            throw new ValidationException(field, "fileType does not match fileName extension.");
        }
        return extension;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).trim().toLowerCase();
    }

    private long resolvedMaxFileSizeBytes() {
        return Math.max(1L, projectFileProperties.getMaxFileSizeBytes());
    }

    private int resolvedMaxFileNameLength() {
        return Math.max(1, projectFileProperties.getMaxFileNameLength());
    }

    private List<String> resolvedAllowedTypes() {
        if (projectFileProperties.getAllowedTypes() == null) {
            return List.of();
        }
        return projectFileProperties.getAllowedTypes()
            .stream()
            .filter(type -> type != null && !type.isBlank())
            .map(type -> type.trim().toLowerCase())
            .distinct()
            .toList();
    }

    private Set<String> resolvedAllowedTypesSet() {
        return projectFileProperties.getAllowedTypes() == null
            ? Set.of()
            : projectFileProperties.getAllowedTypes()
                .stream()
                .filter(type -> type != null && !type.isBlank())
                .map(type -> type.trim().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());
    }

    private int resolvedPresignedUrlExpirySeconds() {
        return Math.max(60, projectFileProperties.getPresignedUrlExpirySeconds());
    }

    private String generateS3Key() {
        return UUID.randomUUID().toString();
    }

    private boolean isValidUuidS3Key(String s3Key) {
        try {
            UUID.fromString(s3Key);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String resolveUserDisplayName(User user) {
        return UserDisplayNameFormatter.format(user);
    }

    private ProjectFileDto toDto(ProjectFile projectFile, String uploadedByRole) {
        return new ProjectFileDto(
            projectFile.getId(),
            projectFile.getFileName(),
            projectFile.getFileType(),
            projectFile.getFileSize(),
            projectFile.getUploadedBy(),
            projectFile.getUploadedByName(),
            Roles.SUPERVISOR.equals(uploadedByRole) ? Roles.SUPERVISOR : Roles.STUDENT,
            projectFile.getCreatedAt(),
            projectFile.getUpdatedAt()
        );
    }
}
