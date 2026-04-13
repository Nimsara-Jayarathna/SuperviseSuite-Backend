package com.supervisesuite.backend.projectfiles.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadUrlRequest {

    @NotBlank(message = "fileName is required.")
    private String fileName;

    @NotBlank(message = "contentType is required.")
    private String contentType;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
