package com.supervisesuite.backend.projectfiles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ConfirmUploadRequest {

    @NotBlank(message = "s3Key is required.")
    private String s3Key;

    @NotBlank(message = "fileName is required.")
    private String fileName;

    @NotBlank(message = "fileType is required.")
    private String fileType;

    @NotNull(message = "fileSize is required.")
    @Positive(message = "fileSize must be greater than zero.")
    private Long fileSize;

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}
