package com.supervisesuite.backend.projects.service.githubv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.dto.ProjectCommitDto;
import com.supervisesuite.backend.projects.dto.ProjectRepositoryMetadataDto;
import com.supervisesuite.backend.projects.entity.GitHubRepositoryEntity;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkCommit;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLinkContributor;
import com.supervisesuite.backend.projects.integration.github.GitHubClient;
import com.supervisesuite.backend.projects.repository.GitHubAccessSourceRepository;
import com.supervisesuite.backend.projects.repository.GitHubRepositoryEntityRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkCommitRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkContributorRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GitHubSyncServiceTest {

    @Mock
    private ProjectRepositoryLinkRepository projectRepositoryLinkRepository;

    @Mock
    private GitHubAccessSourceRepository accessSourceRepository;

    @Mock
    private GitHubRepositoryEntityRepository gitHubRepositoryEntityRepository;

    @Mock
    private ProjectRepositoryLinkCommitRepository commitRepository;

    @Mock
    private ProjectRepositoryLinkContributorRepository contributorRepository;

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private GitHubSyncService service;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new GitHubSyncService(
            projectRepositoryLinkRepository,
            accessSourceRepository,
            gitHubRepositoryEntityRepository,
            commitRepository,
            contributorRepository,
            gitHubClient,
            transactionTemplate,
            new GitHubProperties()
        );
    }

    @Test
    void persistSyncResults_persistsCommitAndContributorGitHubIdentity() {
        UUID linkId = UUID.randomUUID();
        UUID githubRepositoryId = UUID.randomUUID();
        ProjectRepositoryLink link = new ProjectRepositoryLink();
        link.setId(linkId);
        link.setGithubRepositoryId(githubRepositoryId);

        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setId(githubRepositoryId);
        repository.setGithubRepoId(123L);
        repository.setName("repo");
        repository.setFullName("acme/repo");
        repository.setCreatedAt(Instant.now());

        List<ProjectRepositoryLinkCommit> savedCommits = new ArrayList<>();
        List<ProjectRepositoryLinkContributor> savedContributors = new ArrayList<>();

        when(projectRepositoryLinkRepository.findById(linkId)).thenReturn(Optional.of(link));
        when(gitHubRepositoryEntityRepository.findById(githubRepositoryId)).thenReturn(Optional.of(repository));
        when(commitRepository.findByProjectRepositoryLinkIdAndSha(linkId, "abc123")).thenReturn(Optional.empty());
        when(commitRepository.findByProjectRepositoryLinkId(linkId)).thenAnswer(invocation -> savedCommits);
        when(contributorRepository.findByProjectRepositoryLinkIdAndContributorName(linkId, "Alice Doe"))
            .thenReturn(Optional.empty());
        when(commitRepository.save(any(ProjectRepositoryLinkCommit.class))).thenAnswer(invocation -> {
            ProjectRepositoryLinkCommit commit = invocation.getArgument(0);
            savedCommits.add(commit);
            return commit;
        });
        when(contributorRepository.save(any(ProjectRepositoryLinkContributor.class))).thenAnswer(invocation -> {
            ProjectRepositoryLinkContributor contributor = invocation.getArgument(0);
            savedContributors.add(contributor);
            return contributor;
        });

        service.persistSyncResults(
            linkId,
            new ProjectRepositoryMetadataDto(123L, "acme", "repo", "https://github.com/acme/repo", "main"),
            List.of(new ProjectCommitDto(
                "abc123",
                "feat: add avatars",
                "Alice Doe",
                "alice-dev",
                "https://avatars.githubusercontent.com/u/42?v=4",
                Instant.parse("2026-01-01T10:15:30Z")
            ))
        );

        assertThat(savedCommits).hasSize(1);
        assertThat(savedCommits.get(0).getGithubUsername()).isEqualTo("alice-dev");
        assertThat(savedCommits.get(0).getGithubAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/42?v=4");
        assertThat(savedContributors).hasSize(1);
        assertThat(savedContributors.get(0).getContributorName()).isEqualTo("Alice Doe");
        assertThat(savedContributors.get(0).getGithubUsername()).isEqualTo("alice-dev");
        assertThat(savedContributors.get(0).getGithubAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/42?v=4");
    }
}
