package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.projects.dto.JiraHealthDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JiraHealthServiceImplTest {

    @Mock
    private ProjectJiraIssueRepository jiraIssueRepository;

    @Mock
    private JiraHealthClassifier jiraHealthClassifier;

    private JiraHealthServiceImpl service;

    @BeforeEach
    void setUp() {
        JiraHealthMetricsAggregator aggregator = new JiraHealthMetricsAggregator(jiraHealthClassifier);
        service = new JiraHealthServiceImpl(jiraIssueRepository, aggregator);
    }

    @Test
    void getHealthOverview_whenNoIssues_returnsZeroSummary() {
        UUID projectId = UUID.randomUUID();

        when(jiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        JiraHealthDto result = service.getHealthOverview(projectId);

        assertThat(result.completionPercent()).isZero();
        assertThat(result.openIssues()).isZero();
        assertThat(result.overdueIssues()).isZero();
        assertThat(result.highPriorityOpen()).isZero();
        assertThat(result.statusBreakdown().toDo()).isZero();
        assertThat(result.statusBreakdown().inProgress()).isZero();
        assertThat(result.statusBreakdown().done()).isZero();
        assertThat(result.typeDistribution()).isEmpty();
        assertThat(result.bugRatio()).isZero();
        assertThat(result.lastSyncedAt()).isNull();

        verify(jiraIssueRepository, never()).findMaxSyncedAtByProjectId(projectId);
    }

    @Test
    void getHealthOverview_aggregatesOpenDoneRiskAndTypeMetrics() {
        UUID projectId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Instant lastSynced = Instant.parse("2026-04-07T10:00:00Z");

        ProjectJiraIssue doneStory = issue("PRJ-1", "Story", "done", null, "Low");
        ProjectJiraIssue todoBug = issue("PRJ-2", "Bug", "new", yesterday, "High");
        ProjectJiraIssue inProgressTask = issue("PRJ-3", "Task", "indeterminate", tomorrow, "Low");
        ProjectJiraIssue unknownBug = issue("PRJ-4", "Bug", "custom-open", yesterday, "Highest");

        when(jiraIssueRepository.findAllByProjectId(projectId))
            .thenReturn(List.of(doneStory, todoBug, inProgressTask, unknownBug));
        when(jiraIssueRepository.findMaxSyncedAtByProjectId(projectId)).thenReturn(Optional.of(lastSynced));

        when(jiraHealthClassifier.isDoneStatus(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return "done".equals(value);
        });
        when(jiraHealthClassifier.isToDoStatus(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return "new".equals(value);
        });
        when(jiraHealthClassifier.isInProgressStatus(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return "indeterminate".equals(value);
        });
        when(jiraHealthClassifier.isHighPriority(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return "High".equals(value) || "Highest".equals(value);
        });
        when(jiraHealthClassifier.isBugType(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return "Bug".equals(value);
        });

        JiraHealthDto result = service.getHealthOverview(projectId);

        assertThat(result.completionPercent()).isCloseTo(25.0, within(0.0001));
        assertThat(result.openIssues()).isEqualTo(3);
        assertThat(result.overdueIssues()).isEqualTo(2);
        assertThat(result.highPriorityOpen()).isEqualTo(2);
        assertThat(result.statusBreakdown().toDo()).isEqualTo(2);
        assertThat(result.statusBreakdown().inProgress()).isEqualTo(1);
        assertThat(result.statusBreakdown().done()).isEqualTo(1);
        assertThat(result.bugRatio()).isCloseTo(66.6666, within(0.001));
        assertThat(result.typeDistribution()).hasSize(3);
        assertThat(result.typeDistribution().get(0).type()).isEqualTo("Bug");
        assertThat(result.typeDistribution().get(0).count()).isEqualTo(2L);
        assertThat(result.lastSyncedAt()).isEqualTo(lastSynced);
    }

    private static ProjectJiraIssue issue(
            String key,
            String issueType,
            String statusCategoryKey,
            LocalDate dueDate,
            String priorityName) {
        ProjectJiraIssue issue = new ProjectJiraIssue();
        issue.setIssueKey(key);
        issue.setIssueType(issueType);
        issue.setStatusCategoryKey(statusCategoryKey);
        issue.setDueDate(dueDate);
        issue.setPriorityName(priorityName);
        return issue;
    }
}
