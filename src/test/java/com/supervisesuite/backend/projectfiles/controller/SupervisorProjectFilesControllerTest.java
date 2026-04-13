package com.supervisesuite.backend.projectfiles.controller;

import org.springframework.context.annotation.Import;

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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(com.supervisesuite.backend.TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SupervisorProjectFilesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectFileService projectFileService;

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "SUPERVISOR" })
    void listFiles_whenSupervisor_shouldReturnFiles() throws Exception {
        String supervisorId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        ProjectFileListDto.Config config = new ProjectFileListDto.Config(
                10485760L, 50, List.of("pdf", "zip"), 300);

        ProjectFileListDto responseDto = new ProjectFileListDto(List.of(
                new ProjectFileDto(UUID.randomUUID(), "file.pdf", "pdf", 1024L, UUID.randomUUID(), "John Doe",
                        Roles.SUPERVISOR, null, null)),
                config);

        when(projectFileService.listFiles(eq(supervisorId), eq(projectId), eq(ProjectFileAccessRole.SUPERVISOR)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/supervisor/projects/{projectId}/files", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.files[0].fileName").value("file.pdf"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "SUPERVISOR" })
    void getUploadUrl_shouldReturnPresignedUrl() throws Exception {
        String supervisorId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        UploadUrlRequest req = new UploadUrlRequest();
        req.setFileName("doc.pdf");
        req.setContentType("application/pdf");

        when(projectFileService.getUploadUrl(eq(supervisorId), eq(projectId), eq(ProjectFileAccessRole.SUPERVISOR),
                any(UploadUrlRequest.class)))
                .thenReturn(new UploadUrlResponse("https://upload.url", "s3-key-123"));

        mockMvc.perform(post("/api/supervisor/projects/{projectId}/files/upload-url", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value("https://upload.url"))
                .andExpect(jsonPath("$.data.s3Key").value("s3-key-123"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "SUPERVISOR" })
    void confirmUpload_shouldReturnSavedFile() throws Exception {
        String supervisorId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();

        ConfirmUploadRequest req = new ConfirmUploadRequest();
        req.setFileName("doc.pdf");
        req.setFileType("pdf");
        req.setFileSize(2048L);
        req.setS3Key("s3-key-123");

        ProjectFileDto dto = new ProjectFileDto(UUID.randomUUID(), "doc.pdf", "pdf", 2048L, UUID.randomUUID(),
                "Super Visor", Roles.SUPERVISOR, null, null);
        when(projectFileService.confirmUpload(eq(supervisorId), eq(projectId), eq(ProjectFileAccessRole.SUPERVISOR),
                any(ConfirmUploadRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/supervisor/projects/{projectId}/files/confirm", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("doc.pdf"))
                .andExpect(jsonPath("$.data.fileSize").value(2048));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "SUPERVISOR" })
    void getDownloadUrl_shouldReturnUrl() throws Exception {
        String supervisorId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();

        when(projectFileService.getDownloadUrl(eq(supervisorId), eq(projectId), eq(fileId),
                eq(ProjectFileAccessRole.SUPERVISOR)))
                .thenReturn("https://download.url");

        mockMvc.perform(get("/api/supervisor/projects/{projectId}/files/{fileId}/download-url", projectId, fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("https://download.url"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000", roles = { "SUPERVISOR" })
    void deleteFile_shouldReturnNoContent() throws Exception {
        String supervisorId = "123e4567-e89b-12d3-a456-426614174000";
        String projectId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/supervisor/projects/{projectId}/files/{fileId}", projectId, fileId))
                .andExpect(status().isOk());
    }
}
