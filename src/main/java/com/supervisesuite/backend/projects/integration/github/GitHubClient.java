package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import java.util.List;

public interface GitHubClient {
    String buildInstallUrl(String state);

    GitHubAppAuthService.GitHubInstallationContext fetchInstallationContext(Long installationId);

    List<GitHubAppAuthService.GitHubInstallationRepositoryContext> fetchInstallationRepositories(
        Long installationId,
        int pageLimit,
        int pageSize
    );

    ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl, Long installationId);

    List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl, Long installationId);
}
