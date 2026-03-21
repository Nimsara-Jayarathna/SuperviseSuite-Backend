package com.supervisesuite.backend.projects.service;

import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubDashboardDto;
import com.supervisesuite.backend.projects.dto.ProjectGitHubPageDto;
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
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

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

    @Override
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.RecentCommit> getGitHubActivityPage(
        String repositoryUrl,
        int page,
        int size
    ) {
        ProjectGitHubDashboardDto dashboard = getGitHubDashboard(repositoryUrl);
        return paginate(dashboard.getRecentCommits(), page, size);
    }

    @Override
    public ProjectGitHubPageDto<ProjectGitHubDashboardDto.Contributor> getGitHubContributorsPage(
        String repositoryUrl,
        int page,
        int size
    ) {
        ProjectGitHubDashboardDto dashboard = getGitHubDashboard(repositoryUrl);
        return paginate(dashboard.getContributors(), page, size);
    }

    private <T> ProjectGitHubPageDto<T> paginate(List<T> source, int page, int size) {
        List<T> safeSource = source == null ? List.of() : source;
        int normalizedPage = page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = normalizePageSize(size);

        int startIndex = (normalizedPage - 1) * normalizedSize;
        if (startIndex >= safeSource.size()) {
            return new ProjectGitHubPageDto<>(List.of(), normalizedPage, normalizedSize, false);
        }

        int endIndex = Math.min(startIndex + normalizedSize, safeSource.size());
        List<T> items = safeSource.subList(startIndex, endIndex);
        boolean hasMore = endIndex < safeSource.size();

        return new ProjectGitHubPageDto<>(items, normalizedPage, normalizedSize, hasMore);
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
