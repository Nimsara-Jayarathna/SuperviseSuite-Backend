package com.supervisesuite.backend.projectfiles.service;

import com.supervisesuite.backend.config.ProjectFileProperties;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.common.error.UnauthorizedException;
import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.memberships.entity.ProjectMember;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectFileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectFileRepository projectFileRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private ProjectFileProperties projectFileProperties;

    @Captor
    private ArgumentCaptor<ProjectFile> projectFileCaptor;

    private ProjectFileServiceImpl projectFileService;

    private User supervisor;
    private User student;
    private Project project;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectFileService = new ProjectFileServiceImpl(
                userRepository,
                projectRepository,
                projectMemberRepository,
                projectFileRepository,
                storageService,
                projectFileProperties);

        supervisor = new User();
        supervisor.setId(UUID.randomUnique());
        supervisor.setRole(Roles.SUPERVISOR);
        supervisor.setFirstName("Super");
        supervisor.setLastName("Visor");

        student = new User();
        student.setId(UUID.randomUnique());
        student.setRole(Roles.STUDENT);
        student.setFirstName("Stu");
        student.setLastName("Dent");

        projectId = UUID.randomUnique();
        project = new Project();
        project.setId(projectId);
        project.setSupervisorId(supervisor.getId());

        when(projectFileProperties.getMaxFileSizeBytes()).thenReturn((long) (10 * 1024 * 1024)); // 10MB
        when(projectFileProperties.getMaxFileNameLength()).thenReturn(100);
        when(projectFileProperties.getAllowedTypes()).thenReturn(Set.of("pdf", "zip"));
        when(projectFileProperties.getPresignedUrlExpirySeconds()).thenReturn(3600);
    }

    @Test
    void listFiles_whenSupervisor_shouldReturnFiles() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ProjectFile file = new ProjectFile();
        file.setId(UUID.randomUnique());
        file.setProjectId(projectId);
        file.setUploadedBy(supervisor.getId());
        file.setFileName("test.pdf");

        when(projectFileRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(file));
        when(userRepository.findAllById(List.of(supervisor.getId()))).thenReturn(List.of(supervisor));

        ProjectFileListDto result = projectFileService.listFiles(
                supervisor.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.SUPERVISOR);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).id()).isEqualTo(file.getId());
        assertThat(result.files().get(0).fileName()).isEqualTo("test.pdf");
        assertThat(result.files().get(0).uploadedByRole()).isEqualTo(Roles.SUPERVISOR);
    }

    @Test
    void listFiles_whenStudentInProject_shouldReturnFiles() {
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, student.getId())).thenReturn(true);

        when(projectFileRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());

        ProjectFileListDto result = projectFileService.listFiles(
                student.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.STUDENT);

        assertThat(result.files()).isEmpty();
    }

    @Test
    void listFiles_whenStudentNotInProject_shouldThrowNotFound() {
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, student.getId())).thenReturn(false);

        assertThatThrownBy(() -> projectFileService.listFiles(
                student.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.STUDENT)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getUploadUrl_shouldReturnPresignedUrl() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        UploadUrlRequest request = new UploadUrlRequest();
        request.setFileName("document.pdf");
        request.setContentType("application/pdf");

        when(storageService.getUploadUrl(anyString(), eq("application/pdf"))).thenReturn("https://s3.url/upload");

        UploadUrlResponse result = projectFileService.getUploadUrl(
                supervisor.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.SUPERVISOR,
                request);

        assertThat(result.presignedUrl()).isEqualTo("https://s3.url/upload");
        assertThat(result.s3Key()).isNotNull();
    }

    @Test
    void confirmUpload_shouldSaveFile() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setFileName("submission.pdf");
        request.setFileType("application/pdf");
        request.setFileSize(1024L);
        // A valid UUID string is required for s3Key mapping currently
        request.setS3Key(UUID.randomUnique().toString());

        ProjectFile savedFile = new ProjectFile();
        savedFile.setId(UUID.randomUnique());
        savedFile.setProjectId(projectId);
        savedFile.setFileName("submission.pdf");
        savedFile.setFileType("pdf");

        when(projectFileRepository.saveAndFlush(any(ProjectFile.class))).thenReturn(savedFile);

        ProjectFileDto result = projectFileService.confirmUpload(
                supervisor.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.SUPERVISOR,
                request);

        verify(projectFileRepository).saveAndFlush(projectFileCaptor.capture());
        ProjectFile captured = projectFileCaptor.getValue();
        assertThat(captured.getFileName()).isEqualTo("submission.pdf");
        assertThat(captured.getFileSize()).isEqualTo(1024L);
        assertThat(captured.getUploadedBy()).isEqualTo(supervisor.getId());

        assertThat(result.fileName()).isEqualTo("submission.pdf");
    }

    @Test
    void confirmUpload_whenFileTooLarge_shouldThrowValidationException() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setFileName("submission.pdf");
        request.setFileType("application/pdf");
        request.setFileSize(20 * 1024 * 1024L); // 20MB, implies > 10MB limit
        request.setS3Key(UUID.randomUnique().toString());

        assertThatThrownBy(() -> projectFileService.confirmUpload(
                supervisor.getId().toString(),
                projectId.toString(),
                ProjectFileAccessRole.SUPERVISOR,
                request)).isInstanceOf(ValidationException.class).hasMessageContaining("fileSize");
    }

    @Test
    void getDownloadUrl_shouldReturnDownloadUrl() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        UUID fileId = UUID.randomUnique();
        ProjectFile file = new ProjectFile();
        file.setS3Key("some-s3-key");

        when(projectFileRepository.findByIdAndProjectIdAndDeletedAtIsNull(fileId, projectId))
                .thenReturn(Optional.of(file));
        when(storageService.getDownloadUrl("some-s3-key")).thenReturn("https://s3.url/download");

        String downloadUrl = projectFileService.getDownloadUrl(
                supervisor.getId().toString(),
                projectId.toString(),
                fileId.toString(),
                ProjectFileAccessRole.SUPERVISOR);

        assertThat(downloadUrl).isEqualTo("https://s3.url/download");
    }

    @Test
    void deleteFile_shouldSoftDeleteAndRemoveFromS3() {
        when(userRepository.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        UUID fileId = UUID.randomUnique();
        ProjectFile file = new ProjectFile();
        file.setS3Key("some-s3-key");

        when(projectFileRepository.findByIdAndProjectIdAndDeletedAtIsNull(fileId, projectId))
                .thenReturn(Optional.of(file));

        projectFileService.deleteFile(
                supervisor.getId().toString(),
                projectId.toString(),
                fileId.toString());

        assertThat(file.getDeletedAt()).isNotNull();
        verify(storageService).delete("some-s3-key");
        verify(projectFileRepository).save(file);
    }
}
