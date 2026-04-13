package com.supervisesuite.backend.projectfiles.dto;

import java.util.List;

public record ProjectFileListDto(List<ProjectFileDto> files, Config config) {
    public record Config(
        long maxFileSizeBytes,
        int maxFileNameLength,
        List<String> allowedTypes,
        int presignedUrlExpirySeconds
    ) {}
}
