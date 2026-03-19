package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectCommitActivityDto;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import org.springframework.stereotype.Service;

@Service
class ProjectServiceImpl implements ProjectService {

    private final GitHubCommitClient gitHubCommitClient;

    ProjectServiceImpl(GitHubCommitClient gitHubCommitClient) {
        this.gitHubCommitClient = gitHubCommitClient;
    }

    @Override
    public ProjectCommitActivityDto getCommitActivity(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return new ProjectCommitActivityDto(false, java.util.List.of());
        }

        return new ProjectCommitActivityDto(true, gitHubCommitClient.fetchRecentCommits(repositoryUrl));
    }
}