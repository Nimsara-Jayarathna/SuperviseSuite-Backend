package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.supervisesuite.backend.projects.dto.JiraIssueDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIntegration;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIntegrationRepository;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JiraIssueSyncServiceImplTest {

    @Mock
    private ProjectJiraIntegrationRepository jiraIntegrationRepository;

    @Mock
    private ProjectJiraIssueRepository jiraIssueRepository;

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraAuthManager jiraAuthManager;

    @Mock
    private JiraIssueMapper jiraIssueMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    private JiraIssueSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JiraIssueSyncServiceImpl(
            jiraIntegrationRepository,
            jiraIssueRepository,
            jiraClient,
            jiraAuthManager,
            jiraIssueMapper,
            new JiraIssueSyncProcessor(),
            transactionTemplate
        );

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void syncProjectIssues_deduplicatesFetchedIssueKeysBeforeSaveAll() {
        UUID projectId = UUID.randomUUID();

        ProjectJiraIntegration integration = new ProjectJiraIntegration();
        integration.setProjectId(projectId);
        integration.setCloudId("cloud-1");
        integration.setAccessTokenEncrypted("encrypted-token");

        JiraIssueDto duplicateA1 = issue("SCRUM-127");
        JiraIssueDto duplicateA2 = issue("SCRUM-127");
        JiraIssueDto uniqueB = issue("SCRUM-128");

        when(jiraIntegrationRepository.findFirstByProjectIdAndRevokedAtIsNullOrderByConnectedAtDesc(projectId))
            .thenReturn(Optional.of(integration));
        when(jiraAuthManager.getOrRefreshAccessToken(integration)).thenReturn("access-token");
        when(jiraClient.fetchAllIssues("cloud-1", "access-token"))
            .thenReturn(List.of(duplicateA1, duplicateA2, uniqueB));
        when(jiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        doAnswer(invocation -> {
            ProjectJiraIssue target = invocation.getArgument(0);
            JiraIssueDto source = invocation.getArgument(1);
            UUID mappedProjectId = invocation.getArgument(2);
            target.setIssueKey(source.getKey());
            target.setProjectId(mappedProjectId);
            return null;
        }).when(jiraIssueMapper).mapToEntity(
            org.mockito.ArgumentMatchers.any(ProjectJiraIssue.class),
            org.mockito.ArgumentMatchers.any(JiraIssueDto.class),
            org.mockito.ArgumentMatchers.eq(projectId),
            org.mockito.ArgumentMatchers.any()
        );

        service.syncProjectIssues(projectId);

        ArgumentCaptor<List<ProjectJiraIssue>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(jiraIssueRepository).saveAll(saveCaptor.capture());
        List<ProjectJiraIssue> saved = saveCaptor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(ProjectJiraIssue::getIssueKey)
            .containsExactly("SCRUM-127", "SCRUM-128");

        ArgumentCaptor<List<String>> staleKeysCaptor = ArgumentCaptor.forClass(List.class);
        verify(jiraIssueRepository)
            .deleteAllByProjectIdAndIssueKeyNotIn(org.mockito.ArgumentMatchers.eq(projectId), staleKeysCaptor.capture());
        assertThat(staleKeysCaptor.getValue()).containsExactly("SCRUM-127", "SCRUM-128");
    }

    private static JiraIssueDto issue(String key) {
        JiraIssueDto dto = new JiraIssueDto();
        dto.setKey(key);
        return dto;
    }
}
