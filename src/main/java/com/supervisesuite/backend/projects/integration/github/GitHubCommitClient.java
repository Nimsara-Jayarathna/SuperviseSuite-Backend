package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import java.util.List;

public interface GitHubCommitClient {
    List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl);
}