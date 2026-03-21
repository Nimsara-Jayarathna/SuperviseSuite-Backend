package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.integration.github.GitHubCommitClient;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ProjectServiceImpl implements ProjectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final GitHubCommitClient gitHubCommitClient;
    private final ProjectGitHubDashboardMapper dashboardMapper;

    ProjectServiceImpl(
        GitHubCommitClient gitHubCommitClient,
        ProjectGitHubDashboardMapper dashboardMapper
    ) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public ProjectGitHubDashboardDto getGitHubDashboard(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return dashboardMapper.noRepository();
        }

        ProjectRepositoryMetadataDto metadata = null;
        List<ProjectCommitDto> commits = List.of();

        try {
            metadata = gitHubCommitClient.fetchRepositoryMetadata(repositoryUrl);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to fetch repository metadata for {}", repositoryUrl, exception);
        }

        try {
            commits = gitHubCommitClient.fetchRecentCommits(repositoryUrl);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to fetch recent commits for {}", repositoryUrl, exception);
        }

        try {
            return dashboardMapper.toDashboard(repositoryUrl, metadata, commits, Instant.now());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to build GitHub dashboard payload for {}", repositoryUrl, exception);
            return dashboardMapper.toDashboard(repositoryUrl, metadata, List.of(), Instant.now());
        }
    }
}
