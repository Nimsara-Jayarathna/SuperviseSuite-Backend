package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.entity.ProjectRepositoryLink;
import com.supervisesuite.backend.projects.repository.ProjectRepositoryLinkRepository;
import com.supervisesuite.backend.projects.service.githubv2.GitHubSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepositorySyncProviderTest {

    @Mock
    private GitHubProperties gitHubProperties;
    @Mock
    private ProjectRepositoryLinkRepository projectRepositoryLinkRepository;
    @Mock
    private GitHubSyncService gitHubSyncService;

    @InjectMocks
    private GitHubRepositorySyncProvider provider;

    private GitHubProperties.Jobs jobs;
    private GitHubProperties.RepositoryRefresh refreshConfig;

    @BeforeEach
    void setUp() {
        jobs = new GitHubProperties.Jobs();
        refreshConfig = new GitHubProperties.RepositoryRefresh();
        jobs.setRepositoryRefresh(refreshConfig);
    }

    @Test
    void executeSync_ShouldSyncRepositories() {
        when(gitHubProperties.getJobs()).thenReturn(jobs);
        refreshConfig.setEnabled(true);
        
        ProjectRepositoryLink link1 = new ProjectRepositoryLink();
        link1.setId(UUID.randomUUID());
        
        when(projectRepositoryLinkRepository.findByIsEnabledTrueOrderByLastSyncedAtAsc(any(PageRequest.class)))
            .thenReturn(List.of(link1));
            
        provider.executeSync();
        
        verify(gitHubSyncService, times(1)).syncRepository(link1.getId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
    }

    @Test
    void executeSync_ShouldNotInterruptOnFailure() {
        when(gitHubProperties.getJobs()).thenReturn(jobs);
        refreshConfig.setEnabled(true);
        
        ProjectRepositoryLink link1 = new ProjectRepositoryLink();
        link1.setId(UUID.randomUUID());
        
        ProjectRepositoryLink link2 = new ProjectRepositoryLink();
        link2.setId(UUID.randomUUID());
        
        when(projectRepositoryLinkRepository.findByIsEnabledTrueOrderByLastSyncedAtAsc(any(PageRequest.class)))
            .thenReturn(List.of(link1, link2));
            
        doThrow(new RuntimeException("API Limit Exceeded")).when(gitHubSyncService).syncRepository(link1.getId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
        
        provider.executeSync();
        
        verify(gitHubSyncService, times(1)).syncRepository(link1.getId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
        verify(gitHubSyncService, times(1)).syncRepository(link2.getId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
    }
}
