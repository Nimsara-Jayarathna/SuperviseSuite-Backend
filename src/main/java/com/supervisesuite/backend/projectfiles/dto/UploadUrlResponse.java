package com.supervisesuite.backend.projectfiles.dto;

public record UploadUrlResponse(String presignedUrl, String s3Key) {}
