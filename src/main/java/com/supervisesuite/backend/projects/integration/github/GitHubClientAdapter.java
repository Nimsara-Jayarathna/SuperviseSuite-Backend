package com.supervisesuite.backend.projects.integration.github;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
class GitHubClientAdapter implements GitHubClient {

    private final GitHubProperties gitHubProperties;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubCommitClient gitHubCommitClient;

    GitHubClientAdapter(
        GitHubProperties gitHubProperties,
        GitHubAppAuthService gitHubAppAuthService,
        GitHubCommitClient gitHubCommitClient
    ) {
        this.gitHubProperties = gitHubProperties;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubCommitClient = gitHubCommitClient;
    }

    @Override
    public String buildInstallUrl(String state) {
        String installUrl = trimToNull(gitHubProperties.getAppInstallUrl());
        if (installUrl == null) {
            throw new ValidationException("GITHUB_APP_INSTALL_URL", "GitHub App install URL is not configured.");
        }
        return UriComponentsBuilder
            .fromUriString(installUrl)
            .queryParam("state", state)
            .build(true)
            .toUriString();
    }

    @Override
    public GitHubAppAuthService.GitHubInstallationContext fetchInstallationContext(Long installationId) {
        return gitHubAppAuthService.fetchInstallationContext(installationId);
    }

    @Override
    public List<GitHubAppAuthService.GitHubInstallationRepositoryContext> fetchInstallationRepositories(
        Long installationId,
        int pageLimit,
        int pageSize
    ) {
        List<GitHubAppAuthService.GitHubInstallationRepositoryContext> all = new ArrayList<>();
        int normalizedPageLimit = Math.max(1, pageLimit);
        int normalizedPageSize = Math.max(1, pageSize);

        int page = 1;
        while (page <= normalizedPageLimit) {
            GitHubAppAuthService.GitHubInstallationRepositoriesPageContext context =
                gitHubAppAuthService.fetchInstallationRepositories(installationId, page, normalizedPageSize);
            all.addAll(context.repositories());

            int returnedCount = context.repositories().size();
            Long totalCount = context.totalCount();
            boolean hasNext = totalCount != null
                ? (long) page * normalizedPageSize < totalCount
                : returnedCount >= normalizedPageSize && returnedCount > 0;
            if (!hasNext) {
                break;
            }
            page++;
        }

        return all;
    }

    @Override
    public ProjectRepositoryMetadataDto fetchRepositoryMetadata(String repositoryUrl, Long installationId) {
        return gitHubCommitClient.fetchRepositoryMetadata(repositoryUrl, installationId);
    }

    @Override
    public List<ProjectCommitDto> fetchRecentCommits(String repositoryUrl, Long installationId) {
        return gitHubCommitClient.fetchRecentCommits(repositoryUrl, installationId);
    }

    @Override
    public GitHubAppAuthService.GitHubInstallationRepositoriesPageContext fetchInstallationRepositoriesPage(
        Long installationId,
        int page,
        int size
    ) {
        return gitHubAppAuthService.fetchInstallationRepositories(installationId, page, size);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
