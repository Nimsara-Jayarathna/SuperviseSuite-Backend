package com.supervisesuite.backend.projects.scheduler.providers;

import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.service.jira.JiraIssueSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraIssueSyncProviderTest {

    @Mock
    private JiraProperties jiraProperties;
    @Mock
    private ProjectJiraIntegrationRepository jiraIntegrationRepository;
    @Mock
    private JiraIssueSyncService jiraIssueSyncService;

    @InjectMocks
    private JiraIssueSyncProvider provider;

    private JiraProperties.Jobs jobs;
    private JiraProperties.Jobs.IssueSync issueSyncConfig;

    @BeforeEach
    void setUp() {
        jobs = new JiraProperties.Jobs();
        issueSyncConfig = new JiraProperties.Jobs.IssueSync();
        jobs.setIssueSync(issueSyncConfig);
    }

    @Test
    void executeSync_ShouldSyncIssues() {
        when(jiraProperties.getJobs()).thenReturn(jobs);
        issueSyncConfig.setEnabled(true);
        
        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(UUID.randomUUID());
        
        when(jiraIntegrationRepository.findByRevokedAtIsNullOrderByConnectedAtAsc(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(integration)));
            
        provider.executeSync();
        
        verify(jiraIssueSyncService, times(1)).syncProjectIssues(integration.getProjectId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
    }

    @Test
    void executeSync_ShouldNotInterruptOnFailure() {
        when(jiraProperties.getJobs()).thenReturn(jobs);
        issueSyncConfig.setEnabled(true);
        
        ProjectJiraIntegration integration1 = new ProjectJiraIntegration();
        integration1.setProjectId(UUID.randomUUID());
        
        ProjectJiraIntegration integration2 = new ProjectJiraIntegration();
        integration2.setProjectId(UUID.randomUUID());
        
        when(jiraIntegrationRepository.findByRevokedAtIsNullOrderByConnectedAtAsc(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(integration1, integration2)));
            
        doThrow(new RuntimeException("API Limit Exceeded")).when(jiraIssueSyncService).syncProjectIssues(integration1.getProjectId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
        
        provider.executeSync();
        
        verify(jiraIssueSyncService, times(1)).syncProjectIssues(integration1.getProjectId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
        verify(jiraIssueSyncService, times(1)).syncProjectIssues(integration2.getProjectId(), com.supervisesuite.backend.projects.service.SyncAttemptSource.CRON);
    }
}
