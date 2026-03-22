package com.supervisesuite.backend.projects.integration.github;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class GitHubCommitClientImplTest {

    @Mock
    private ObjectProvider<GitHubAppAuthService> gitHubAppAuthServiceProvider;

    private GitHubCommitClientImpl client;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.com");
        properties.setDefaultBranch("main");
        properties.setCommitsPageSize(50);

        when(gitHubAppAuthServiceProvider.getIfAvailable()).thenReturn(null);
        client = new GitHubCommitClientImpl(RestClient.builder(), properties, gitHubAppAuthServiceProvider);
    }

    @Test
    void fetchRecentCommits_emptyRepositoryUrl_throwsValidationException() {
        assertThatThrownBy(() -> client.fetchRecentCommits("   ", null))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getDetails())
                    .anySatisfy(detail ->
                        org.assertj.core.api.Assertions.assertThat(detail.getIssue())
                            .contains("Repository URL is required")
                    )
            );
    }

    @Test
    void fetchRecentCommits_nonGithubUrl_throwsValidationException() {
        assertThatThrownBy(() -> client.fetchRecentCommits("https://gitlab.com/acme/repo", null))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getDetails())
                    .anySatisfy(detail ->
                        org.assertj.core.api.Assertions.assertThat(detail.getIssue())
                            .contains("Only GitHub repository URLs are supported")
                    )
            );
    }

    @Test
    void fetchRepositoryMetadata_invalidRepositoryPath_throwsValidationException() {
        assertThatThrownBy(() -> client.fetchRepositoryMetadata("https://github.com/acme", null))
            .isInstanceOfSatisfying(ValidationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getDetails())
                    .anySatisfy(detail ->
                        org.assertj.core.api.Assertions.assertThat(detail.getIssue())
                            .contains("owner and repository name")
                    )
            );
    }
}
