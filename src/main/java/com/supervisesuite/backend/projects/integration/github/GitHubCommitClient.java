package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import java.util.List;

public interface GitHubCommitClient {
    default List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl) {
        return fetchRecentCommits(repositoryUrl, null);
    }

    List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl, Long installationId);

    default ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl) {
        return fetchRepositoryMetadata(repositoryUrl, null);
    }

    ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl, Long installationId);
}
