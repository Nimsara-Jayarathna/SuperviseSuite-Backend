package com.supervisesuite.backend.projectfiles.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervisesuite.backend.common.constants.Roles;
import com.supervisesuite.backend.projectfiles.dto.ConfirmUploadRequest;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileDto;
import com.supervisesuite.backend.projectfiles.dto.ProjectFileListDto;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlRequest;
import com.supervisesuite.backend.projectfiles.dto.UploadUrlResponse;
import com.supervisesuite.backend.projectfiles.service.ProjectFileAccessRole;
import com.supervisesuite.backend.projectfiles.service.ProjectFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StudentProjectFilesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectFileService projectFileService;

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "STUDENT" })
    void listFiles_whenStudent_shouldReturnFiles() throws Exception {
        String studentId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        ProjectFileListDto.Config config = new ProjectFileListDto.Config(
                10485760L, 50, Set.of("pdf", "zip"), 300);

        ProjectFileListDto responseDto = new ProjectFileListDto(List.of(
                new ProjectFileDto(UUID.randomUUID(), "student-file.pdf", "pdf", 1024L, "s3-key", Roles.STUDENT, null,
                        "Jane Doe", null)),
                config);

        when(projectFileService.listFiles(eq(studentId), eq(projectId), eq(ProjectFileAccessRole.STUDENT)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/student/projects/{projectId}/files", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.files[0].fileName").value("student-file.pdf"))
                .andExpect(jsonPath("$.data.files[0].uploadedByRole").value(Roles.STUDENT));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "STUDENT" })
    void getUploadUrl_shouldReturnPresignedUrl() throws Exception {
        String studentId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        UploadUrlRequest req = new UploadUrlRequest();
        req.setFileName("submission.zip");
        req.setContentType("application/zip");

        when(projectFileService.getUploadUrl(eq(studentId), eq(projectId), eq(ProjectFileAccessRole.STUDENT),
                any(UploadUrlRequest.class)))
                .thenReturn(new UploadUrlResponse("https://student.upload.url", "s3-key-student"));

        mockMvc.perform(post("/api/student/projects/{projectId}/files/upload-url", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value("https://student.upload.url"))
                .andExpect(jsonPath("$.data.s3Key").value("s3-key-student"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "STUDENT" })
    void confirmUpload_shouldReturnSavedFile() throws Exception {
        String studentId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        ConfirmUploadRequest req = new ConfirmUploadRequest();
        req.setFileName("submission.zip");
        req.setFileType("zip");
        req.setFileSize(4096L);
        req.setS3Key("s3-key-student");

        ProjectFileDto dto = new ProjectFileDto(UUID.randomUUID(), "submission.zip", "zip", 4096L, "s3-key-student",
                Roles.STUDENT, null, "Jane Doe", null);
        when(projectFileService.confirmUpload(eq(studentId), eq(projectId), eq(ProjectFileAccessRole.STUDENT),
                any(ConfirmUploadRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/student/projects/{projectId}/files/confirm", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("submission.zip"))
                .andExpect(jsonPath("$.data.fileSize").value(4096));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "STUDENT" })
    void getDownloadUrl_shouldReturnUrl() throws Exception {
        String studentId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();

        when(projectFileService.getDownloadUrl(eq(studentId), eq(projectId), eq(fileId),
                eq(ProjectFileAccessRole.STUDENT)))
                .thenReturn("https://student.download.url");

        mockMvc.perform(get("/api/student/projects/{projectId}/files/{fileId}/download", projectId, fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://student.download.url"));
    }
}