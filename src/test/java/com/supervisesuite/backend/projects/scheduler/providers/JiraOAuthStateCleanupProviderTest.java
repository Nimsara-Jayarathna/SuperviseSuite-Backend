package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.projects.repository.ProjectJiraOAuthStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JiraOAuthStateCleanupProviderTest {

    @Mock
    private ProjectJiraOAuthStateRepository repository;

    @InjectMocks
    private JiraOAuthStateCleanupProvider provider;

    @Test
    void executeCleanup_ShouldInvokeRepositoryDelete() {
        provider.executeCleanup();
        verify(repository, times(1)).deleteExpired(any());
    }
}
