package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.config.GitHubProperties;
import com.supervisesuite.backend.projects.repository.ProjectGitHubAccessRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubAccessRequestCleanupProviderTest {

    @Mock
    private GitHubProperties gitHubProperties;

    @Mock
    private ProjectGitHubAccessRequestRepository repository;

    @InjectMocks
    private GitHubAccessRequestCleanupProvider provider;

    private GitHubProperties.Jobs jobs;
    private GitHubProperties.AccessRequestCleanup cleanupConfig;

    @BeforeEach
    void setUp() {
        jobs = new GitHubProperties.Jobs();
        cleanupConfig = new GitHubProperties.AccessRequestCleanup();
        jobs.setAccessRequestCleanup(cleanupConfig);
    }

    @Test
    void executeCleanup_ShouldInvokeRepositoryDelete() {
        when(gitHubProperties.getJobs()).thenReturn(jobs);
        
        provider.executeCleanup();
        
        verify(repository, times(1)).deleteExpiredRequestsByStatuses(anyList(), any());
        verify(repository, times(1)).clearExpiredResultTokens(any());
    }
}
