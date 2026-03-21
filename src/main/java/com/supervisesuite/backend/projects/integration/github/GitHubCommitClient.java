package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import java.util.List;

public interface GitHubCommitClient {
    List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl);

    ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl);
}
