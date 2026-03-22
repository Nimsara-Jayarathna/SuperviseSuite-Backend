package com.supervisesuite.backend.projects.integration.github;

import java.time.Instant;

public interface GitHubAppAuthService {
    String createAppJwt();

    GitHubInstallationToken createInstallationAccessToken(Long installationId);

    GitHubInstallationContext fetchInstallationContext(Long installationId);

    java.util.List<GitHubInstallationRepositoryContext> fetchInstallationRepositories(Long installationId);

    record GitHubInstallationToken(String token, Instant expiresAt) {
    }

    record GitHubInstallationContext(
        Long installationId,
        Long accountId,
        String accountLogin,
        String accountType
    ) {
    }

    record GitHubInstallationRepositoryContext(
        Long repositoryId,
        String repositoryName,
        String fullName,
        String ownerLogin,
        String htmlUrl,
        String defaultBranch
    ) {
    }
}
