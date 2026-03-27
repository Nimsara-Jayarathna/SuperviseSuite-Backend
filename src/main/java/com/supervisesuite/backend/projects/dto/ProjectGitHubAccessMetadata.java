package com.supervisesuite.backend.projects.dto;

public record ProjectGitHubAccessMetadata(
    Long authorizedInstallationId,
    Integer accessibleRepositoryCount,
    String accessScope,
    String primaryRepositoryUrl
) {
}
