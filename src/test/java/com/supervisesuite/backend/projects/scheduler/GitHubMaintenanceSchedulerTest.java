package com.supervisesuite.backend.projects.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubMaintenanceSchedulerTest {

    @Mock
    private ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;

    @Mock
    private ProjectRepositoryLinkRepository projectRepositoryLinkRepository;

    @Mock
    private GitHubSyncService gitHubSyncService;

    private GitHubProperties properties;
    private GitHubMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setJobs(new GitHubProperties.Jobs());
        scheduler = new GitHubMaintenanceScheduler(
            properties,
            projectGitHubAccessRequestRepository,
            projectRepositoryLinkRepository,
            gitHubSyncService
        );
    }

    @Test
    void cleanupExpiredAccessRequestTokens_disabled_doesNothing() {
        GitHubProperties.AccessRequestCleanup cleanup = new GitHubProperties.AccessRequestCleanup();
        cleanup.setEnabled(false);
        properties.getJobs().setAccessRequestCleanup(cleanup);

        scheduler.cleanupExpiredAccessRequestTokens();

        verify(projectGitHubAccessRequestRepository, never()).deleteExpiredRequestsByStatuses(any(), any());
        verify(projectGitHubAccessRequestRepository, never()).clearExpiredResultTokens(any());
    }

    @Test
    void cleanupExpiredAccessRequestTokens_enabled_executesCleanupQueries() {
        GitHubProperties.AccessRequestCleanup cleanup = new GitHubProperties.AccessRequestCleanup();
        cleanup.setEnabled(true);
        properties.getJobs().setAccessRequestCleanup(cleanup);

        scheduler.cleanupExpiredAccessRequestTokens();

        verify(projectGitHubAccessRequestRepository).deleteExpiredRequestsByStatuses(any(), any());
        verify(projectGitHubAccessRequestRepository).clearExpiredResultTokens(any());
    }

    @Test
    void refreshLinkedGitHubRepositories_executesSyncForAllLinks() {
        GitHubProperties.RepositoryRefresh refresh = new GitHubProperties.RepositoryRefresh();
        refresh.setEnabled(true);
        refresh.setBatchSize(10);
        properties.getJobs().setRepositoryRefresh(refresh);

        UUID activeProjectId = UUID.randomUUID();
        UUID deletedProjectId = UUID.randomUUID();

        ProjectRepositoryLink activeLink = link(activeProjectId, "https://github.com/acme/active");
        ProjectRepositoryLink deletedLink = link(deletedProjectId, "https://github.com/acme/deleted");

        when(projectRepositoryLinkRepository.findByIsEnabledTrueOrderByLastSyncedAtAsc(any()))
            .thenReturn(List.of(activeLink, deletedLink));

        scheduler.refreshLinkedGitHubRepositories();

        verify(gitHubSyncService).syncRepository(activeLink.getId());
        verify(gitHubSyncService).syncRepository(deletedLink.getId());
    }

    private static ProjectRepositoryLink link(UUID projectId, String url) {
        ProjectRepositoryLink link = new ProjectRepositoryLink();
        link.setId(UUID.randomUUID());
        link.setProjectId(projectId);
        link.setRepositoryUrl(url);
        link.setCreatedAt(Instant.now());
        return link;
    }
}
