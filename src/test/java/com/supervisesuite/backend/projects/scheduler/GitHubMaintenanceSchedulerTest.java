package com.supervisesuite.backend.projects.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.Project;
import com.supervisesuite.backend.projects.entity.ProjectRepository;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryCacheRepository;
import com.supervisesuite.backend.projects.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class GitHubMaintenanceSchedulerTest {

    @Mock
    private ProjectGitHubAccessRequestRepository projectGitHubAccessRequestRepository;

    @Mock
    private ProjectRepositoryCacheRepository projectRepositoryCacheRepository;

    @Mock
    private com.supervisesuite.backend.projects.repository.ProjectRepository projectRepository;

    @Mock
    private ProjectService projectService;

    private GitHubProperties properties;
    private GitHubMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setJobs(new GitHubProperties.Jobs());
        scheduler = new GitHubMaintenanceScheduler(
            properties,
            projectGitHubAccessRequestRepository,
            projectRepositoryCacheRepository,
            projectRepository,
            projectService
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
    void refreshLinkedGitHubRepositories_onlyRefreshesActiveProjects() {
        GitHubProperties.RepositoryRefresh refresh = new GitHubProperties.RepositoryRefresh();
        refresh.setEnabled(true);
        refresh.setBatchSize(10);
        properties.getJobs().setRepositoryRefresh(refresh);

        UUID activeProjectId = UUID.randomUUID();
        UUID deletedProjectId = UUID.randomUUID();

        ProjectRepository activeRepository = repository(activeProjectId, "https://github.com/acme/active");
        ProjectRepository deletedRepository = repository(deletedProjectId, "https://github.com/acme/deleted");

        when(projectRepositoryCacheRepository.findByProviderIgnoreCaseAndIsPrimaryTrueAndRepositoryUrlIsNotNull(
            any(),
            any()
        )).thenReturn(new PageImpl<>(List.of(activeRepository, deletedRepository)));

        Project activeProject = new Project();
        activeProject.setId(activeProjectId);
        activeProject.setCreatedAt(Instant.now());
        activeProject.setName("Active");

        when(projectRepository.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(List.of(activeProjectId, deletedProjectId)))
            .thenReturn(List.of(activeProject));

        scheduler.refreshLinkedGitHubRepositories();

        verify(projectService).refreshGitHubData(activeProjectId, "https://github.com/acme/active");
        verify(projectService, never()).refreshGitHubData(deletedProjectId, "https://github.com/acme/deleted");
    }

    private static ProjectRepository repository(UUID projectId, String url) {
        ProjectRepository repository = new ProjectRepository();
        repository.setId(UUID.randomUUID());
        repository.setProjectId(projectId);
        repository.setProvider("github");
        repository.setRepositoryUrl(url);
        repository.setIsPrimary(true);
        repository.setCreatedAt(Instant.now());
        return repository;
    }
}
