package com.supervisesuite.backend.projectfiles.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectFileDto(
    UUID id,
    String fileName,
    String fileType,
    Long fileSize,
    UUID uploadedBy,
    String uploadedByName,
    String uploadedByRole,
    Instant createdAt,
    Instant updatedAt
) {}
