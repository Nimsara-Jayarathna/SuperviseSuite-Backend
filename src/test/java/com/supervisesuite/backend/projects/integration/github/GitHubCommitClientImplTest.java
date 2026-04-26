package com.supervisesuite.backend.projects.integration.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class GitHubCommitClientImplTest {

    @Mock
    private ObjectProvider<GitHubAppAuthService> gitHubAppAuthServiceProvider;

    private GitHubCommitClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.com");
        properties.setDefaultBranch("main");
        properties.setCommitsPageSize(50);

        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        when(gitHubAppAuthServiceProvider.getIfAvailable()).thenReturn(null);
        client = new GitHubCommitClientImpl(restClientBuilder, properties, gitHubAppAuthServiceProvider);
    }

    @Test
    void fetchRecentCommits_mapsGitHubAuthorIdentity() {
        server.expect(once(), requestTo("https://api.github.com/repos/acme/repo/commits?per_page=50&page=1"))
            .andExpect(header(HttpHeaders.USER_AGENT, "SuperviseSuite-Backend"))
            .andRespond(withSuccess("""
                [
                  {
                    "sha": "abc123",
                    "commit": {
                      "message": "feat: add dashboard",
                      "author": {
                        "name": "Alice Doe",
                        "date": "2026-01-01T10:15:30Z"
                      }
                    },
                    "author": {
                      "login": "alice-dev",
                      "avatar_url": "https://avatars.githubusercontent.com/u/42?v=4"
                    }
                  }
                ]
                """, MediaType.APPLICATION_JSON));

        List<ProjectCommitDto> commits = client.fetchRecentCommits("https://github.com/acme/repo", null);

        assertThat(commits).hasSize(1);
        ProjectCommitDto commit = commits.get(0);
        assertThat(commit.getAuthor()).isEqualTo("Alice Doe");
        assertThat(commit.getAuthorName()).isEqualTo("Alice Doe");
        assertThat(commit.getGithubUsername()).isEqualTo("alice-dev");
        assertThat(commit.getGithubAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/42?v=4");
        server.verify();
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
