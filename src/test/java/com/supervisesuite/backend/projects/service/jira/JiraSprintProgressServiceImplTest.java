package com.supervisesuite.backend.projects.service.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.supervisesuite.backend.config.JiraProperties;
import com.supervisesuite.backend.projects.dto.JiraSprintProgressDto;
import com.supervisesuite.backend.projects.entity.ProjectJiraIssue;
import com.supervisesuite.backend.projects.repository.ProjectJiraIssueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JiraSprintProgressServiceImplTest {

    @Mock
    private ProjectJiraIssueRepository jiraIssueRepository;

    @Mock
    private JiraHealthClassifier jiraHealthClassifier;

    private JiraSprintProgressServiceImpl service;

    @BeforeEach
    void setUp() {
                service = new JiraSprintProgressServiceImpl(jiraIssueRepository, jiraHealthClassifier, new JiraProperties());
    }

    @Test
    void getSprintProgress_whenNoIssues_returnsEmptySections() {
        UUID projectId = UUID.randomUUID();
        when(jiraIssueRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        JiraSprintProgressDto result = service.getSprintProgress(projectId);

        assertThat(result.sprintDataAvailable()).isFalse();
        assertThat(result.activeSprint()).isNull();
        assertThat(result.recentSprints()).isEmpty();
        assertThat(result.velocityWeeks()).isEmpty();
        assertThat(result.backlogGrowing()).isFalse();
    }

    @Test
    void getSprintProgress_aggregatesSprintAndVelocityData() {
        UUID projectId = UUID.randomUUID();

        ProjectJiraIssue s2DoneWithSp = issue(
                2L,
                "Sprint 2",
                "active",
                Instant.parse("2026-04-07T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"),
                "done",
                Instant.parse("2026-04-06T10:00:00Z"),
                Instant.parse("2026-04-09T12:00:00Z"),
                new BigDecimal("5"));
        ProjectJiraIssue s2OpenNoSp = issue(
                2L,
                "Sprint 2",
                "active",
                Instant.parse("2026-04-07T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"),
                "indeterminate",
                Instant.parse("2026-04-10T10:00:00Z"),
                null,
                null);
        ProjectJiraIssue s3Done = issue(
                3L,
                "Sprint 3",
                "closed",
                Instant.parse("2026-03-10T00:00:00Z"),
                Instant.parse("2026-04-30T00:00:00Z"),
                "done",
                Instant.parse("2026-04-15T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                new BigDecimal("3"));
        ProjectJiraIssue s1Open = issue(
                1L,
                "Sprint 1",
                "closed",
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-15T00:00:00Z"),
                "new",
                Instant.parse("2026-03-03T10:00:00Z"),
                null,
                null);
        ProjectJiraIssue noSprint = issue(
                null,
                null,
                null,
                null,
                null,
                "done",
                Instant.parse("2026-03-04T10:00:00Z"),
                Instant.parse("2026-03-05T10:00:00Z"),
                null);

        when(jiraIssueRepository.findAllByProjectId(projectId))
                .thenReturn(List.of(s2DoneWithSp, s2OpenNoSp, s3Done, s1Open, noSprint));
        when(jiraHealthClassifier.isDoneStatus(anyString())).thenAnswer(invocation ->
                "done".equals(invocation.getArgument(0, String.class)));

        JiraSprintProgressDto result = service.getSprintProgress(projectId);

        assertThat(result.sprintDataAvailable()).isTrue();
        assertThat(result.activeSprint()).isNotNull();
        assertThat(result.activeSprint().sprintId()).isEqualTo(2L);
        assertThat(result.activeSprint().issuesDone()).isEqualTo(1);
        assertThat(result.activeSprint().issuesTotal()).isEqualTo(2);
        assertThat(result.activeSprint().sprintStartIssueCount()).isEqualTo(1);
        assertThat(result.activeSprint().completionPercent()).isEqualTo(50.0);
        assertThat(result.activeSprint().sprintPointsAvailable()).isTrue();
        assertThat(result.activeSprint().sprintPointsDone()).isEqualTo(5.0);
        assertThat(result.activeSprint().sprintPointsTotal()).isEqualTo(5.0);

        assertThat(result.recentSprints()).hasSize(3);
        assertThat(result.recentSprints().get(0).sprintId()).isEqualTo(3L);
        assertThat(result.recentSprints().get(1).sprintId()).isEqualTo(2L);
        assertThat(result.recentSprints().get(2).sprintId()).isEqualTo(1L);

        assertThat(result.velocityWeeks()).isNotEmpty();
        assertThat(result.velocityWeeks().stream().anyMatch(week -> week.averageCycleDays() != null)).isTrue();
        assertThat(result.backlogGrowing()).isTrue();
    }

    private static ProjectJiraIssue issue(
            Long sprintId,
            String sprintName,
            String sprintState,
            Instant sprintStart,
            Instant sprintEnd,
            String statusCategory,
            Instant createdAt,
            Instant resolvedAt,
            BigDecimal storyPoints) {
        ProjectJiraIssue issue = new ProjectJiraIssue();
        issue.setSprintId(sprintId);
        issue.setSprintName(sprintName);
        issue.setSprintState(sprintState);
        issue.setSprintStartDate(sprintStart);
        issue.setSprintEndDate(sprintEnd);
        issue.setStatusCategoryKey(statusCategory);
        issue.setJiraCreatedAt(createdAt);
        issue.setResolutionDate(resolvedAt);
        issue.setStoryPoints(storyPoints);
        return issue;
    }
}
