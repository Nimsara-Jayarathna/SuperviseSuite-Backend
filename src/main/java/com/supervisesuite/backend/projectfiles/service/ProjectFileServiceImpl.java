package com.supervisesuite.backend.projectfiles.service;

import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.memberships.repository.ProjectMemberRepository;
import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
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
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProjectFileServiceImpl implements ProjectFileService {

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L;

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectFileRepository projectFileRepository;
    private final StorageService storageService;

    ProjectFileServiceImpl(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        ProjectFileRepository projectFileRepository,
        StorageService storageService
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectFileRepository = projectFileRepository;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectFileDto> listFiles(String authenticatedUserId, String projectId, ProjectFileAccessRole accessRole) {
        User user = resolveAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        return projectFileRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(parsedProjectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UploadUrlResponse getUploadUrl(
        String authenticatedUserId,
        String projectId,
        ProjectFileAccessRole accessRole,
        UploadUrlRequest request
    ) {
        User user = resolveAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        String fileName = requireTrimmed(request.getFileName(), "fileName");
        String contentType = requireTrimmed(request.getContentType(), "contentType");
        String s3Key = generateS3Key(parsedProjectId, fileName);
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
        User user = resolveAuthenticatedUser(authenticatedUserId, accessRole);
        UUID parsedProjectId = parseProjectId(projectId);
        requireProjectAccess(user, parsedProjectId, accessRole);

        String s3Key = requireTrimmed(request.getS3Key(), "s3Key");
        String fileName = requireTrimmed(request.getFileName(), "fileName");
        String fileType = requireTrimmed(request.getFileType(), "fileType");
        Long fileSize = request.getFileSize();

        if (!s3Key.startsWith("projects/" + parsedProjectId + "/")) {
            throw new ValidationException("s3Key", "s3Key is invalid for this project.");
        }
        if (fileSize == null || fileSize <= 0) {
            throw new ValidationException("fileSize", "fileSize must be greater than zero.");
        }
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("fileSize", "fileSize exceeds the maximum allowed size.");
        }

        ProjectFile projectFile = new ProjectFile();
        projectFile.setProjectId(parsedProjectId);
        projectFile.setS3Key(s3Key);
        projectFile.setFileName(fileName);
        projectFile.setFileType(fileType);
        projectFile.setFileSize(fileSize);
        projectFile.setUploadedBy(user.getId());
        projectFile.setUploadedByName(resolveUserDisplayName(user));

        ProjectFile saved = projectFileRepository.save(projectFile);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(
        String authenticatedUserId,
        String projectId,
        String fileId,
        ProjectFileAccessRole accessRole
    ) {
        User user = resolveAuthenticatedUser(authenticatedUserId, accessRole);
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
        User supervisor = resolveAuthenticatedUser(authenticatedUserId, ProjectFileAccessRole.SUPERVISOR);
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

    private User resolveAuthenticatedUser(String authenticatedUserId, ProjectFileAccessRole accessRole) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Authentication required.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        String expectedRole = accessRole == ProjectFileAccessRole.SUPERVISOR ? Roles.SUPERVISOR : Roles.STUDENT;
        if (!expectedRole.equals(user.getRole())) {
            throw new UnauthorizedException("Authentication required.");
        }

        return user;
    }

    private Project requireProjectAccess(User user, UUID projectId, ProjectFileAccessRole accessRole) {
        if (accessRole == ProjectFileAccessRole.SUPERVISOR) {
            return projectRepository.findByIdAndSupervisor_IdAndDeletedAtIsNull(projectId, user.getId())
                .orElseThrow(EntityNotFoundException::new);
        }

        boolean hasMembership = projectMemberRepository.existsByUserIdAndProjectIdAndMemberRole(
            user.getId(),
            projectId,
            Roles.STUDENT
        );
        if (!hasMembership) {
            throw new EntityNotFoundException();
        }

        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(EntityNotFoundException::new);
    }

    private UUID parseProjectId(String projectId) {
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
    }

    private UUID parseFileId(String fileId) {
        try {
            return UUID.fromString(fileId);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException();
        }
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

    private String generateS3Key(UUID projectId, String fileName) {
        String sanitized = fileName
            .replaceAll("[^A-Za-z0-9._-]", "_")
            .replaceAll("_+", "_");
        String safeName = sanitized.isBlank() ? "file" : sanitized;
        return "projects/" + projectId + "/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeName;
    }

    private String resolveUserDisplayName(User user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
            + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private ProjectFileDto toDto(ProjectFile projectFile) {
        return new ProjectFileDto(
            projectFile.getId(),
            projectFile.getFileName(),
            projectFile.getFileType(),
            projectFile.getFileSize(),
            projectFile.getUploadedBy(),
            projectFile.getUploadedByName(),
            projectFile.getCreatedAt(),
            projectFile.getUpdatedAt()
        );
    }
}
